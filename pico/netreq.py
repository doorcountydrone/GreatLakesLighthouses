# Dual-stack HTTP(S) and NTP for IPv4-broken / IPv6-first hotspots (T-Mobile).
# IPv4 first (home and hotel stay the same), then IPv6 DNS, then NAT64.

import gc
import socket
import struct
import utime as time

try:
    import tls
except ImportError:
    tls = None
try:
    import ssl
except ImportError:
    ssl = None

_NAT64 = "64:ff9b::"
_NTP_DELTA = 2208988800
_AF6 = getattr(socket, "AF_INET6", None)
_preferred = None


def _set_preferred(how):
    global _preferred
    _preferred = how


def _parse_url(url):
    url = str(url).strip()
    https = url.startswith("https://")
    rest = url.split("://", 1)[-1]
    hostpath = rest.split("/", 1)
    host = hostpath[0]
    path = "/" + (hostpath[1] if len(hostpath) > 1 else "")
    port = 443 if https else 80
    if host.startswith("["):
        end = host.find("]")
        if end > 1:
            name = host[1:end]
            extra = host[end + 1:]
            if extra.startswith(":") and extra[1:].isdigit():
                port = int(extra[1:])
            return https, name, port, path
    if ":" in host:
        name, p = host.rsplit(":", 1)
        if p.isdigit() and "." in name:
            return https, name, int(p), path
    return https, host, port, path


def _ipv4_to_nat64(ip):
    parts = [int(x) for x in str(ip).split(".")]
    if len(parts) != 4:
        raise ValueError("not ipv4")
    return "%s%x:%x" % (_NAT64, (parts[0] << 8) | parts[1], (parts[2] << 8) | parts[3])


def _discover_nat64_prefix():
    global _NAT64
    if _AF6 is None:
        return
    try:
        infos = socket.getaddrinfo("ipv4only.arpa", 80, _AF6, socket.SOCK_STREAM)
        addr = infos[0][-1][0]
        if isinstance(addr, bytes):
            addr = addr.decode()
        addr = str(addr).split("%")[0].strip("[]")
        if addr.lower().startswith("64:ff9b:"):
            _NAT64 = "64:ff9b::"
            return
        # Pref64 /96: drop the last 32 bits (last two hextets).
        hextets = addr.split(":")
        while hextets and hextets[-1] == "":
            hextets.pop()
        if len(hextets) >= 2:
            _NAT64 = ":".join(hextets[:-2]) + "::"
    except Exception:
        pass


def _connect_family(host, port, family, timeout):
    last = OSError("no address")
    infos = socket.getaddrinfo(host, port, family, socket.SOCK_STREAM)
    for ai in infos:
        sock = None
        try:
            sock = socket.socket(ai[0], ai[1], ai[2])
            sock.settimeout(timeout)
            sock.connect(ai[-1])
            return sock
        except Exception as e:
            last = e
            if sock is not None:
                try:
                    sock.close()
                except Exception:
                    pass
    raise last


def _try_how(host, port, timeout, how):
    t = timeout
    if how == "4" and _preferred != "4":
        t = min(int(timeout), 5)
    if how == "4":
        return _connect_family(host, port, socket.AF_INET, t)
    if _AF6 is None:
        raise OSError("no ipv6")
    if how == "6":
        return _connect_family(host, port, _AF6, t)
    infos = socket.getaddrinfo(host, port, socket.AF_INET, socket.SOCK_STREAM)
    ipv4 = infos[0][-1][0]
    _discover_nat64_prefix()
    return _connect_family(_ipv4_to_nat64(ipv4), port, _AF6, t)


def _paths():
    order = ["4", "6", "nat64"]
    if _preferred in order:
        return [_preferred] + [h for h in order if h != _preferred]
    return order


def _wrap_tls(sock, server_hostname):
    if tls is not None:
        ctx = tls.SSLContext(tls.PROTOCOL_TLS_CLIENT)
        try:
            ctx.verify_mode = tls.CERT_NONE
        except Exception:
            pass
        return ctx.wrap_socket(sock, server_hostname=server_hostname)
    if ssl is None:
        raise OSError("no tls")
    try:
        return ssl.wrap_socket(sock, server_hostname=server_hostname)
    except TypeError:
        return ssl.wrap_socket(sock)


def _sendall(sock, data):
    if isinstance(data, str):
        data = data.encode()
    mv = memoryview(data)
    off = 0
    while off < len(mv):
        n = sock.write(mv[off:]) if hasattr(sock, "write") else sock.send(mv[off:])
        if n is None:
            n = len(mv) - off
        if n <= 0:
            raise OSError("send")
        off += n


def _recv(sock, n=512):
    if hasattr(sock, "read"):
        data = sock.read(n)
        return data or b""
    return sock.recv(n) or b""


def _read_http(sock, dest=None, max_size=49152):
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = _recv(sock, 256)
        if not chunk:
            break
        buf += chunk
        if len(buf) > 8192:
            raise OSError("headers too large")
    sep = buf.find(b"\r\n\r\n")
    if sep < 0:
        raise OSError("no http headers")
    header = buf[:sep]
    body = buf[sep + 4:]
    line = header.split(b"\r\n", 1)[0]
    parts = line.split()
    code = int(parts[1]) if len(parts) > 1 else 0
    loc = ""
    for raw in header.split(b"\r\n"):
        low = raw.lower()
        if low.startswith(b"location:"):
            loc = raw.split(b":", 1)[1].strip().decode()
            break
    if dest is not None:
        dest.write(body)
        total = len(body)
        while True:
            chunk = _recv(sock, 512)
            if not chunk:
                break
            dest.write(chunk)
            total += len(chunk)
        return code, loc, total
    chunks = [body]
    total = len(body)
    while total < max_size:
        chunk = _recv(sock, 512)
        if not chunk:
            break
        chunks.append(chunk)
        total += len(chunk)
    return code, loc, b"".join(chunks)


def _request(url, timeout, dest=None):
    gc.collect()
    https, host, port, path = _parse_url(url)
    req = (
        "GET %s HTTP/1.0\r\nHost: %s\r\nUser-Agent: GreatLakesLighthouses\r\nConnection: close\r\n\r\n"
        % (path, host)
    )
    last = OSError("connect failed")
    for how in _paths():
        sock = None
        wrapped = None
        try:
            sock = _try_how(host, port, timeout, how)
            wrapped = _wrap_tls(sock, host) if https else sock
            sock = None
            _sendall(wrapped, req)
            result = _read_http(wrapped, dest)
            _set_preferred(how)
            print("HTTP", how, host)
            return result
        except Exception as e:
            last = e
        finally:
            for s in (wrapped, sock):
                if s is not None:
                    try:
                        s.close()
                    except Exception:
                        pass
            gc.collect()
    raise last


def get_text(url, timeout=12):
    current = url
    last = OSError("get failed")
    for _ in range(4):
        try:
            code, loc, body = _request(current, timeout)
        except Exception as e:
            last = e
            break
        if code in (301, 302, 303, 307, 308) and loc:
            if loc.startswith("/"):
                https, host, port, _path = _parse_url(current)
                scheme = "https" if https else "http"
                current = "%s://%s%s" % (scheme, host, loc)
            else:
                current = loc
            continue
        if code != 200:
            raise OSError("HTTP %s" % code)
        if isinstance(body, bytes):
            return body.decode()
        return body
    raise last


def download(url, dest_path, timeout=30):
    current = url
    last = OSError("download failed")
    for _ in range(4):
        f = None
        try:
            f = open(dest_path, "wb")
            code, loc, _total = _request(current, timeout, dest=f)
        except Exception as e:
            last = e
            if f is not None:
                try:
                    f.close()
                except Exception:
                    pass
            try:
                import os
                os.remove(dest_path)
            except Exception:
                pass
            break
        try:
            f.close()
        except Exception:
            pass
        if code in (301, 302, 303, 307, 308) and loc:
            if loc.startswith("/"):
                https, host, port, _path = _parse_url(current)
                scheme = "https" if https else "http"
                current = "%s://%s%s" % (scheme, host, loc)
            else:
                current = loc
            continue
        if code != 200:
            try:
                import os
                os.remove(dest_path)
            except Exception:
                pass
            raise OSError("HTTP %s" % code)
        return True
    raise last


def _ntp_send(host, family, timeout):
    msg = b"\x1b" + bytes(47)
    infos = socket.getaddrinfo(host, 123, family, socket.SOCK_DGRAM)
    last = OSError("ntp")
    for ai in infos:
        sock = None
        try:
            sock = socket.socket(ai[0], ai[1], ai[2])
            sock.settimeout(timeout)
            sock.sendto(msg, ai[-1])
            data = sock.recv(48)
            if data and len(data) >= 48:
                val = struct.unpack("!12I", data)[10]
                return val - _NTP_DELTA
        except Exception as e:
            last = e
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass
    raise last


def ntp(timeout=4):
    last = OSError("ntp failed")
    order = _paths()
    for host in ("time.google.com", "time.cloudflare.com", "pool.ntp.org"):
        for how in order:
            try:
                if how == "4":
                    sec = _ntp_send(host, socket.AF_INET, timeout)
                elif _AF6 is None:
                    continue
                elif how == "6":
                    sec = _ntp_send(host, _AF6, timeout)
                else:
                    infos = socket.getaddrinfo(host, 123, socket.AF_INET, socket.SOCK_DGRAM)
                    sec = _ntp_send(_ipv4_to_nat64(infos[0][-1][0]), _AF6, timeout)
                _set_preferred(how)
                print("NTP", how, host)
                return sec
            except Exception as e:
                last = e
    raise last


def apply_ntp(seconds):
    tm = time.gmtime(seconds)
    import machine
    machine.RTC().datetime((tm[0], tm[1], tm[2], tm[6] + 1, tm[3], tm[4], tm[5], 0))

#!/usr/bin/env python3
"""Minimal RCON client (Source RCON protocol) for the CI smoke test; Python standard library only.

    rcon.py HOST PORT PASSWORD "command" ["command" ...]

Prints the response of every command. Waits up to 60 s for the server to accept the connection.
Two pseudo commands run on this side: "!sleep SECONDS", and "!until SECONDS TEXT command ..." which repeats the command every
two seconds until its answer contains TEXT (or the time is up) and prints the last answer.
"""
import socket
import struct
import sys
import time


def packet(request_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("connection closed")
        data += chunk
    return data


def read_packet(sock: socket.socket):
    (length,) = struct.unpack("<i", read_exact(sock, 4))
    payload = read_exact(sock, length)
    request_id, kind = struct.unpack("<ii", payload[:8])
    return request_id, kind, payload[8:-2].decode("utf-8", "replace")


def connect(host: str, port: int, password: str, wait: float = 60.0) -> socket.socket:
    deadline = time.time() + wait
    last = None
    while time.time() < deadline:
        try:
            sock = socket.create_connection((host, port), timeout=10)
            sock.sendall(packet(1, 3, password))
            request_id, _, _ = read_packet(sock)
            if request_id == -1:
                raise PermissionError("rcon login refused")
            return sock
        except (OSError, ConnectionError) as e:
            last = e
            time.sleep(1)
    raise TimeoutError(f"rcon not reachable: {last}")


def run(host: str, port: int, password: str, commands: list) -> int:
    sock = connect(host, port, password)
    sock.settimeout(600)   # selftest generates chunks and can take a while
    for i, cmd in enumerate(commands):
        if cmd.startswith("!sleep "):
            time.sleep(float(cmd.split()[1]))
            print(f"> {cmd}", flush=True)
            continue
        if cmd.startswith("!until "):
            _, seconds, text, real = cmd.split(" ", 3)
            deadline = time.time() + float(seconds)
            body = ""
            while True:
                sock.sendall(packet(100 + i, 2, real))
                try:
                    _, _, body = read_packet(sock)
                except (OSError, ConnectionError):
                    body = "(connection closed)"
                    break
                if text in body or time.time() > deadline:
                    break
                time.sleep(2)
            print(f"> {real}\n{body}", flush=True)
            continue
        sock.sendall(packet(100 + i, 2, cmd))
        try:
            _, _, body = read_packet(sock)
        except (OSError, ConnectionError):
            body = "(connection closed)"
        print(f"> {cmd}\n{body}", flush=True)
    sock.close()
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 5:
        print(__doc__)
        sys.exit(2)
    sys.exit(run(sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4:]))

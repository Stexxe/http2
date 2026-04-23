from common import serve
import h2.events

def handler(conn, event):
    if isinstance(event, h2.events.RequestReceived):
        conn.send_headers(event.stream_id, [(b':status', b'204')], end_stream=True)

serve(handler)
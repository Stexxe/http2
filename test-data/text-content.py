from common import serve
import h2.events

def handler(conn, event):
    if isinstance(event, h2.events.RequestReceived):
        conn.send_headers(event.stream_id, [(b':status', b'200')], end_stream=False)
        conn.send_data(event.stream_id, b'hello', end_stream=True)

serve(handler)
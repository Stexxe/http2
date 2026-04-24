from common import serve
import h2.events

bodies = {}
def handler(conn, event):
    if isinstance(event, h2.events.RequestReceived):
        headers = dict(event.headers)
        if headers[b':method'] == b'POST' and headers[b':path'] == b'/post':
            bodies[event.stream_id] = b''
        else:
            conn.send_headers(event.stream_id, [(b':status', b'404')], end_stream=True)

    elif isinstance(event, h2.events.DataReceived):
        conn.acknowledge_received_data(event.flow_controlled_length, event.stream_id)
        if event.stream_id in bodies:
            bodies[event.stream_id] += event.data

    elif isinstance(event, h2.events.StreamEnded):
        if event.stream_id in bodies:
            body = bodies.pop(event.stream_id)
            conn.send_headers(event.stream_id, [(b':status', b'200')], end_stream=False)
            conn.send_data(event.stream_id, b'client sent: ' + body, end_stream=True)

serve(handler)
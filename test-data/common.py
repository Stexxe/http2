import socket
import h2.connection
import h2.config
import h2.events

def serve(handler):
  server = socket.socket()
  server.bind(('', 0))
  server.listen(1)
  print(server.getsockname()[1], flush=True) # print the port number

  sock, _ = server.accept()
  config = h2.config.H2Configuration(client_side=False)
  conn = h2.connection.H2Connection(config=config)
  conn.initiate_connection()
  sock.sendall(conn.data_to_send())

  while True:
      data = sock.recv(65535)
      if not data:
          break
      for event in conn.receive_data(data):
          handler(conn, event)
      sock.sendall(conn.data_to_send())
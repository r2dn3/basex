package org.basex.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.basex.core.BaseXException;
import org.basex.core.Command;
import org.basex.core.Commands.Cmd;
import org.basex.core.Context;
import org.basex.core.Prop;
import org.basex.io.ArrayOutput;
import org.basex.io.BufferInput;
import org.basex.io.PrintOutput;
import org.basex.server.trigger.TriggerNotification;
import org.basex.util.Token;

/**
 * This wrapper sends commands to the server instance over a socket
 * connection. It extends the {@link Session} class:
 *
 * <ul>
 * <li> A socket instance is created by the constructor.</li>
 * <li> The {@link #execute} method sends database commands to the server.
 * All strings are encoded as UTF8 and suffixed by a zero byte.</li>
 * <li> If the command has been successfully executed,
 * the result string is read.</li>
 * <li> Next, the command info string is read.</li>
 * <li> A last byte is next sent to indicate if command execution
 * was successful (0) or not (1).</li>
 * <li> {@link #close} closes the session by sending the {@link Cmd#EXIT}
 * command to the server.</li>
 * </ul>
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 */
public final class ClientSession extends Session {
  /** Socket closed message. */
  static final String SOCKET_CLOSED = "Socket closed";
  /** Socket reference. */
  final Socket socket;
  /** Server output. */
  final PrintOutput sout;
  /** Server input. */
  final InputStream sin;
  /** Lock object. */
  Object lock = new Object();
  /** Output stream. */
  OutputStream sink;
  /** Trigger notifications. */
  Map<String, List<TriggerNotification>> tn;

  /**
   * Constructor, specifying the database context and the
   * login and password.
   * @param context database context
   * @param user user name
   * @param pw password
   * @throws IOException I/O exception
   */
  public ClientSession(final Context context, final String user,
      final String pw) throws IOException {
    this(context, user, pw, null);
  }

  /**
   * Constructor, specifying the database context and the
   * login and password.
   * @param context database context
   * @param user user name
   * @param pw password
   * @param output client output; if set to {@code null}, results will
   * be returned as strings.
   * @throws IOException I/O exception
   */
  public ClientSession(final Context context, final String user,
      final String pw, final OutputStream output) throws IOException {
    this(context.prop.get(Prop.HOST), context.prop.num(Prop.PORT),
        user, pw, output);
  }

  /**
   * Constructor, specifying the server host:port combination and the
   * login and password.
   * @param host server name
   * @param port server port
   * @param user user name
   * @param pw password
   * @throws IOException I/O exception
   */
  public ClientSession(final String host, final int port,
      final String user, final String pw) throws IOException {
    this(host, port, user, pw, null);
  }

  /**
   * Constructor, specifying the server host:port combination and the
   * login and password.
   * @param host server name
   * @param port server port
   * @param user user name
   * @param pw password
   * @param output client output; if set to {@code null}, results will
   * be returned as strings.
   * @throws IOException I/O exception
   */
  public ClientSession(final String host, final int port, final String user,
      final String pw, final OutputStream output) throws IOException {

    super(output);

    // initialize trigger notifications
    tn = new HashMap<String, List<TriggerNotification>>();

    // 5 seconds timeout
    socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), 5000);
    sin = socket.getInputStream();

    // receive timestamp
    final BufferInput bi = new BufferInput(sin);
    final String ts = bi.readString();

    // send user name and hashed password/timestamp
    sout = PrintOutput.get(socket.getOutputStream());
    send(user);
    send(Token.md5(Token.md5(pw) + ts));
    sout.flush();

    // receive success flag
    if(!ok(bi)) throw new LoginException();
    startListener();
  }

  /**
   * Starts the listener thread.
   * @throws IOException I/O exception
   */
  private void startListener() throws IOException {
    new Thread() {
      BufferInput bi = new BufferInput(sin);
      @Override
      public void run() {
        try {
          while(true) {
            final ArrayOutput o = new ArrayOutput();
            int l;
            while((l = bi.read()) != 0)
              if (sink != null)
                sink.write(l);
              else
                o.write(l);
            info = bi.readString();
            boolean ok = bi.read() == 0 ? true : false;
            if(!ok) {
              final String inf = info();
              if(!inf.isEmpty()) {
                if (sink != null)
                  sink.write(inf.getBytes());
                else
                  o.write(inf.getBytes());
              }
            }
            // Set sink as unset.
            if (sink != null) {
              sink.flush();
              sink = null;
            } else {
              String res = o.toString();
              if(!"".equals(res)) {
                int idx = res.indexOf(' ');
                String name = res.substring(0, idx);
                String val = res.substring(idx + 1);
                if(tn.size() > 0) for(TriggerNotification t : tn.get(name))
                  t.update(val);
              }
            }
            synchronized(lock) {
              lock.notifyAll();
            }
            o.close();
          }
        } catch(IOException e) {
          if (!SOCKET_CLOSED.equals(e.getMessage())) e.printStackTrace();
        }
      }
    }.start();
  }

  @Override
  public void create(final String name, final InputStream input)
      throws BaseXException {
    try {
      sout.write(8);
      send(name);
      send(input);
    } catch(final IOException ex) {
      throw new BaseXException(ex);
    }
  }

  @Override
  public void add(final String name, final String target,
      final InputStream input) throws BaseXException {
    try {
      sout.write(9);
      send(name);
      send(target);
      send(input);
    } catch(final IOException ex) {
      throw new BaseXException(ex);
    }
  }

  /**
   * Sends the specified stream to the server.
   * @param input input stream
   * @throws IOException I/O exception
   */
  private void send(final InputStream input) throws IOException {
    int l;
    while((l = input.read()) != -1) sout.write(l);
    sout.write(0);
    sout.flush();
    final BufferInput bi = new BufferInput(sin);
    info = bi.readString();
    if(!ok(bi)) throw new IOException(info);
  }

  @Override
  public ClientQuery query(final String query) throws BaseXException {
    return new ClientQuery(query, this);
  }

  /**
   * Creates a trigger.
   * @param name trigger name
   * @throws BaseXException exception
   */
  public void createTrigger(final String name) throws BaseXException {
    execute("create trigger " + name);
  }

  /**
   * Drops a trigger.
   * @param name trigger name
   * @throws BaseXException exception
   */
  public void dropTrigger(final String name) throws BaseXException {
    execute("drop trigger " + name);
  }

  /**
   * Attaches to a trigger.
   * @param name trigger name
   * @param notification trigger notification
   * @throws BaseXException exception
   */
  public void attachTrigger(final String name,
      final TriggerNotification notification) throws BaseXException {
    execute("attach trigger " + name);

    if (tn.get(name) == null)
      tn.put(name, new ArrayList<TriggerNotification>(1));

    tn.get(name).add(notification);
  }

  /**
   * Detaches from a trigger.
   * @param name trigger name
   * @throws BaseXException exception
   */
  public void detachTrigger(final String name) throws BaseXException {
    // remove trigger notification.
    tn.remove(name);
    execute("detach trigger " + name);
  }

  /**
   * Executes a trigger.
   * @param query query string
   * @param name trigger name
   * @param notification trigger notification
   * @throws BaseXException exception
   */
  public void trigger(final String query, final String name,
      final String notification) throws BaseXException {
    execute("xquery db:trigger(" + query + ", " + name + ", '" +
        notification + "')");
  }

  @Override
  public void close() throws IOException {
    send(Cmd.EXIT.toString());
    socket.close();
  }

  /**
   * Sends a string to the server.
   * @param s string to be sent
   * @throws IOException I/O exception
   */
  void send(final String s) throws IOException {
    sout.print(s);
    sout.write(0);
    sout.flush();
  }

  /**
   * Checks the next success flag.
   * @param bi buffer input
   * @return value of check
   * @throws IOException I/O exception
   */
  boolean ok(final BufferInput bi) throws IOException {
    return bi.read() == 0;
  }

  @Override
  public void execute(final String cmd, final OutputStream os)
      throws BaseXException {
    synchronized(lock) {
      this.sink = os;
      try {
        send(cmd);
      } catch(IOException e) {
        throw new BaseXException(e);
      }
      try {
        lock.wait();
      } catch(InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void execute(final Command cmd, final OutputStream os)
      throws BaseXException {
    execute(cmd.toString(), os);
  }
}

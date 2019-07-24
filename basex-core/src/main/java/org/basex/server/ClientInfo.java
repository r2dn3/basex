package org.basex.server;

import org.basex.core.*;
import org.basex.core.users.*;
import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Client info.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public interface ClientInfo {
  /**
   * Returns the host and port of a client.
   * @return address of client
   */
  String clientAddress();

  /**
   * Returns the name of the current client.
   * @return name of client
   */
  String clientName();

  /**
   * Tries to convert the specified object to the string representation of an XQuery value.
   * @param id id (can be {@code null})
   * @param ctx database context
   * @return return string or {@code null}
   */
  default String clientName(final Object id, final Context ctx) {
    try {
      if(id instanceof Item) return Token.string(((Item) id).string(null));
    } catch(final QueryException ex) {
      Util.debug(ex);
    }

    // check for authenticated user
    final User user = ctx.user();
    if(user != null) return user.name();

    // user is unknown
    return null;
  }
}

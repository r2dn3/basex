package org.basex.query.func.fn;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public final class FnQName extends StandardFunc {
  @Override
  public QNm item(final QueryContext qc, final InputInfo ii) throws QueryException {
    final byte[] uri = toEmptyToken(exprs[0], qc);
    final byte[] name = toToken(exprs[1], qc);
    final byte[] str = !contains(name, ':') && eq(uri, XML_URI) ? concat(XMLC, name) : name;
    if(!XMLToken.isQName(str)) throw valueError(AtomType.QNM, name, info);
    final QNm qname = new QNm(str, uri);
    if(qname.hasPrefix() && uri.length == 0) throw valueError(AtomType.URI, qname.uri(), info);
    return qname;
  }
}

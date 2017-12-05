package org.basex.query.func.fn;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public final class FnNumber extends ContextFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Item item = ctxArg(0, qc).atomItem(qc, info);
    if(item == null) return Dbl.NAN;
    if(item.type == AtomType.DBL) return item;
    try {
      if(info != null) info.internal(true);
      return AtomType.DBL.cast(item, qc, sc, info);
    } catch(final QueryException ex) {
      return Dbl.NAN;
    } finally {
      if(info != null) info.internal(false);
    }
  }

  @Override
  protected Expr opt(final CompileContext cc) {
    final boolean arg = exprs.length != 0;
    final Expr expr = arg ? exprs[0] : cc.qc.focus.value;
    // number(1e1) -> 1e1
    // $double[number() = 1] -> $double[. = 1]
    return expr == null || !expr.seqType().eq(SeqType.DBL_O) ? this :
      arg ? expr : new ContextValue(info).optimize(cc);
  }
}

package org.basex.query.func.fn;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public final class FnForEach extends StandardFunc {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    final Iter iter = exprs[0].iter(qc);
    final FItem func = checkArity(exprs[1], 1, qc);

    return new Iter() {
      Iter ir = Empty.ITER;

      @Override
      public Item next() throws QueryException {
        do {
          final Item it = qc.next(ir);
          if(it != null) return it;
          final Item item = iter.next();
          if(item == null) return null;
          ir = func.invokeValue(qc, info, item).iter();
        } while(true);
      }
    };
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    final Iter iter = exprs[0].iter(qc);
    final FItem func = checkArity(exprs[1], 1, qc);

    final ValueBuilder vb = new ValueBuilder(qc);
    for(Item item; (item = qc.next(iter)) != null;) vb.add(func.invokeValue(qc, info, item));
    return vb.value();
  }

  @Override
  protected Expr opt(final CompileContext cc) throws QueryException {
    final Expr expr1 = exprs[0];
    final SeqType st1 = expr1.seqType();
    if(st1.zero()) return expr1;

    coerceFunc(1, cc, SeqType.ITEM_ZM, st1.type.seqType());

    // assign type after coercion (expression might have changed)
    final Expr expr2 = exprs[1];
    final Type t2 = expr2.seqType().type;
    if(t2 instanceof FuncType) exprType.assign(((FuncType) t2).declType.type);

    final long sz1 = expr1.size();
    if(allAreValues(false) && sz1 <= UNROLL_LIMIT) {
      // unroll the loop
      final Value seq = (Value) expr1;
      final Expr[] results = new Expr[(int) sz1];
      for(int i = 0; i < sz1; i++) {
        results[i] = new DynFuncCall(info, sc, expr2, seq.itemAt(i)).optimize(cc);
      }
      cc.info(QueryText.OPTUNROLL_X, this);
      return new List(info, results).optimize(cc);
    }

    return this;
  }
}

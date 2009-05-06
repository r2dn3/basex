package org.basex.core.proc;

import static org.basex.Text.*;
import java.io.IOException;
import org.basex.BaseX;
import org.basex.core.Context;
import org.basex.core.Process;
import org.basex.core.Prop;
import org.basex.data.Data;
import org.basex.data.DataText;
import org.basex.data.DiskData;
import org.basex.io.IO;

/**
 * Opens an existing database.
 * 
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
public final class Open extends Process {
  /**
   * Constructor.
   * @param name name of database
   */
  public Open(final String name) {
    super(STANDARD, name);
  }

  @Override
  protected boolean exec() {
    final String db = args[0].replaceAll("(.*)\\..*", "$1");
    if(!IO.dbpath(db).exists() && !db.equals(DataText.S_DEEPFS))
      return error(DBNOTFOUND, db);

    // close old database
    context.close();

    try {
      Data data = Context.POOL.pin(db);
      if(data == null) {
        // open new database instance
        data = open(db);
        Context.POOL.add(data);
      }
      context.data(data);

      if(Prop.info) {
        if(data.meta.oldindex) info(INDUPDATE + NL);
        info(DBOPENED, perf.getTimer());
      }
      return true;
    } catch(final IOException ex) {
      BaseX.debug(ex);
      final String msg = ex.getMessage();
      return error(msg.length() != 0 ? msg : DBOPENERR);
    }
  }

  /**
   * Static method for opening a database.
   * No warnings are thrown; instead, an empty reference is returned.
   * @param db database to be opened
   * @return data reference
   * @throws IOException exception
   */
  public static Data open(final String db) throws IOException {
    return new DiskData(db);
  }
}

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;

/** One-time, root-only scope cleanup. Never changes profiles or module settings. */
public final class SpoofScopeMaintenance {
    public static void main(String[] args) {
        if (Process.myUid() != 0) throw new SecurityException("root required");
        boolean clear = args.length == 1 && "clear-disabled-spoof".equals(args[0]);
        String pkg = "com.spoofmydevice";
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/adb/lspd/config/modules_config.db", null,
                (clear ? SQLiteDatabase.OPEN_READWRITE : SQLiteDatabase.OPEN_READONLY)
                        | SQLiteDatabase.NO_LOCALIZED_COLLATORS)) {
            if (db.getVersion() != 3) throw new IllegalStateException("Unsupported schema");
            if (clear) {
                db.beginTransaction();
                try (Cursor c = db.rawQuery("SELECT mid,enabled FROM modules WHERE module_pkg_name=?", new String[]{pkg})) {
                    if (!c.moveToFirst() || c.getInt(1) != 0) throw new IllegalStateException("Disable SpoofMyDevice first");
                    int n = db.delete("scope", "mid=? AND NOT (app_pkg_name=? AND user_id=0)",
                            new String[]{c.getString(0), pkg});
                    db.setTransactionSuccessful();
                    System.out.println("CLEARED_TARGET_SCOPES=" + n);
                } finally { db.endTransaction(); }
            }
            try (Cursor c = db.rawQuery("SELECT m.module_pkg_name,m.enabled,s.app_pkg_name,s.user_id FROM modules m "
                    + "LEFT JOIN scope s ON m.mid=s.mid ORDER BY m.module_pkg_name,s.user_id,s.app_pkg_name", null)) {
                while (c.moveToNext()) System.out.println(c.getString(0)+"|"+c.getInt(1)+"|"+c.getString(2)+"|"+c.getInt(3));
            }
        }
    }
}

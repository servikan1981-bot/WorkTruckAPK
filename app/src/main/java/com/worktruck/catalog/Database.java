package com.worktruck.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;

public class Database {
    private final Context context;
    private SQLiteDatabase db;

    public Database(Context c){ context=c; }

    public synchronized void open() throws IOException {
        File target = new File(context.getFilesDir(), "products.db");
        File assetCheck = target;

        // Always replace a broken/old local copy with the DB bundled in this build.
        boolean mustCopy = !target.exists() || target.length() < 1000000;
        if (!mustCopy) {
            SQLiteDatabase test = null;
            try {
                test = SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                try (Cursor c = test.rawQuery("SELECT article,name,qty FROM products LIMIT 1", null)) {
                    mustCopy = !c.moveToFirst();
                }
            } catch (Exception e) {
                mustCopy = true;
            } finally {
                if (test != null) try { test.close(); } catch (Exception ignored) {}
            }
        }

        if (mustCopy) {
            File tmp = new File(context.getFilesDir(), "products.db.tmp");
            try(InputStream in=context.getAssets().open("products.db"); OutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[1024*1024];
                int n;
                while((n=in.read(buf))>0) out.write(buf,0,n);
                out.flush();
            }
            if (target.exists() && !target.delete()) throw new IOException("Не удалось обновить локальную базу");
            if (!tmp.renameTo(target)) throw new IOException("Не удалось установить базу номенклатуры");
        }

        if (db != null && db.isOpen()) db.close();
        db=SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    public synchronized int getProductCount(){
        if(db==null || !db.isOpen()) return 0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products",null)){
            return c.moveToFirst()?c.getInt(0):0;
        } catch(Exception e){ return 0; }
    }

    public synchronized List<Product> search(String query,String warehouse){
        List<Product> out=new ArrayList<>();
        if(db==null || !db.isOpen()) return out;

        String raw = query==null ? "" : query.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        String lower = raw.toLowerCase(Locale.ROOT);
        String title = raw.isEmpty() ? raw : raw.substring(0,1).toUpperCase(Locale.ROOT) + raw.substring(1).toLowerCase(Locale.ROOT);

        StringBuilder where=new StringBuilder("qty>0");
        List<String> args=new ArrayList<>();

        if(!raw.isEmpty()){
            where.append(" AND (")
                 .append("article LIKE ? OR manufacturer_no LIKE ? OR ")
                 .append("manufacturer LIKE ? OR manufacturer LIKE ? OR ")
                 .append("name LIKE ? OR name LIKE ? OR name LIKE ? OR ")
                 .append("series LIKE ? OR series LIKE ?")
                 .append(")");
            args.add(raw+"%");
            args.add(raw+"%");
            args.add("%"+raw+"%");
            args.add("%"+upper+"%");
            args.add("%"+raw+"%");
            args.add("%"+title+"%");
            args.add("%"+upper+"%");
            args.add("%"+raw+"%");
            args.add("%"+upper+"%");
        }

        if(warehouse!=null && !warehouse.isEmpty() && !warehouse.equals("Все склады")){
            where.append(" AND warehouse=?");
            args.add(warehouse);
        }

        // IMPORTANT: products.db has exactly these columns. Do not reference non-existent brand/search fields.
        String sql="SELECT id,article,manufacturer_no,manufacturer,name,price,qty,warehouse,series " +
                "FROM products WHERE "+where+
                " ORDER BY qty DESC, name LIMIT 150";

        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){
                Product p=new Product();
                p.id=c.getLong(0);
                p.article=s(c.getString(1));
                p.manufacturerNo=s(c.getString(2));
                p.manufacturer=s(c.getString(3));
                p.brand=p.manufacturer;
                p.name=s(c.getString(4));
                p.price=c.isNull(5)?0:c.getDouble(5);
                p.qty=c.isNull(6)?0:c.getDouble(6);
                p.warehouse=s(c.getString(7));
                p.series=s(c.getString(8));
                out.add(p);
            }
        }
        return out;
    }

    private String s(String value){ return value==null ? "" : value; }
}

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
    public void open() throws IOException {
        File target = new File(context.getFilesDir(), "products.db");
        if(!target.exists() || target.length()<1000000){
            try(InputStream in=context.getAssets().open("products.db"); OutputStream out=new FileOutputStream(target)){
                byte[] buf=new byte[1024*1024]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
            }
        }
        db=SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }
    public int getProductCount(){
        if(db==null) return 0; try(Cursor c=db.rawQuery("SELECT count(*) FROM products",null)){ return c.moveToFirst()?c.getInt(0):0; } catch(Exception e){return 0;}
    }
    public List<Product> search(String query,String warehouse){
        List<Product> out=new ArrayList<>(); if(db==null)return out;
        String q=(query==null?"":query.trim().toLowerCase(Locale.ROOT));
        StringBuilder where=new StringBuilder("qty>0"); List<String> args=new ArrayList<>();
        if(!q.isEmpty()){ where.append(" AND (article LIKE ? OR manufacturer_no LIKE ? OR search LIKE ?)"); args.add(q+"%");args.add(q+"%");args.add("%"+q+"%"); }
        if(warehouse!=null && !warehouse.isEmpty() && !warehouse.equals("Все склады")){ where.append(" AND warehouse=?");args.add(warehouse); }
        String sql="SELECT id,article,manufacturer_no,manufacturer,brand,name,price,qty,warehouse,series FROM products WHERE "+where+" ORDER BY CASE WHEN lower(article)=? THEN 0 WHEN lower(manufacturer_no)=? THEN 1 ELSE 2 END, qty DESC, name LIMIT 150";
        args.add(q);args.add(q);
        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){ while(c.moveToNext()){ Product p=new Product();p.id=c.getLong(0);p.article=c.getString(1);p.manufacturerNo=c.getString(2);p.manufacturer=c.getString(3);p.brand=c.getString(4);p.name=c.getString(5);p.price=c.getDouble(6);p.qty=c.getDouble(7);p.warehouse=c.getString(8);p.series=c.getString(9);out.add(p);} }
        return out;
    }
}

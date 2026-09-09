package com.worktruck.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;

public class Database {
    private final Context context;
    private SQLiteDatabase db;

    public static final String[] CATEGORIES = {
        "Двигатель",
        "КПП и сцепление",
        "Шасси и рама",
        "Кабина и кузов",
        "Крепёж",
        "Электрика",
        "Тормозная система",
        "Топливная и выхлоп",
        "Охлаждение и климат",
        "Прочее"
    };

    public Database(Context c){ context=c; }

    public synchronized void open() throws IOException {
        File target=new File(context.getFilesDir(),"products.db");
        boolean copy=!target.exists()||target.length()<1000000;
        if(!copy){
            SQLiteDatabase test=null;
            try{
                test=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
                try(Cursor c=test.rawQuery("SELECT category FROM products LIMIT 1",null)){ copy=!c.moveToFirst(); }
            }catch(Exception e){copy=true;}
            finally{if(test!=null)try{test.close();}catch(Exception ignored){}}
        }
        if(copy){
            File tmp=new File(context.getFilesDir(),"products.db.tmp");
            try(InputStream in=context.getAssets().open("products.db");OutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[1024*1024];int n;while((n=in.read(buf))>0)out.write(buf,0,n);out.flush();
            }
            if(target.exists()&&!target.delete())throw new IOException("Не удалось обновить локальную базу");
            if(!tmp.renameTo(target))throw new IOException("Не удалось установить базу номенклатуры");
        }
        if(db!=null&&db.isOpen())db.close();
        db=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
    }

    public synchronized int getProductCount(){
        if(db==null||!db.isOpen())return 0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products WHERE qty>0",null)){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}
    }

    public synchronized int getCategoryCount(String category){
        if(db==null||!db.isOpen())return 0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products WHERE qty>0 AND category=?",new String[]{category})){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}
    }

    public synchronized Map<String,Integer> getCategoryCounts(){
        LinkedHashMap<String,Integer> out=new LinkedHashMap<>();for(String c:CATEGORIES)out.put(c,0);
        if(db==null||!db.isOpen())return out;
        try(Cursor c=db.rawQuery("SELECT category,count(*) FROM products WHERE qty>0 GROUP BY category",null)){
            while(c.moveToNext())out.put(s(c.getString(0)),c.getInt(1));
        }catch(Exception ignored){}
        return out;
    }

    public synchronized List<Product> search(String query,String warehouse){return searchCategory(null,query,warehouse);}

    public synchronized List<Product> searchCategory(String category,String query,String warehouse){
        List<Product> out=new ArrayList<>();if(db==null||!db.isOpen())return out;
        String raw=query==null?"":query.trim();
        StringBuilder where=new StringBuilder("qty>0");List<String> args=new ArrayList<>();
        if(category!=null&&!category.trim().isEmpty()){where.append(" AND category=?");args.add(category);}
        if(!raw.isEmpty()){
            where.append(" AND (article LIKE ? OR manufacturer_no LIKE ? OR manufacturer LIKE ? OR name LIKE ? OR series LIKE ?)");
            args.add(raw+"%");args.add(raw+"%");args.add("%"+raw+"%");args.add("%"+raw+"%");args.add("%"+raw+"%");
        }
        if(warehouse!=null&&!warehouse.isEmpty()&&!warehouse.equals("Все склады")){where.append(" AND warehouse=?");args.add(warehouse);}
        String sql="SELECT id,article,manufacturer_no,manufacturer,name,price,qty,warehouse,series FROM products WHERE "+where+" ORDER BY name LIMIT 300";
        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){
                Product p=new Product();p.id=c.getLong(0);p.article=s(c.getString(1));p.manufacturerNo=s(c.getString(2));p.manufacturer=s(c.getString(3));p.brand=p.manufacturer;p.name=s(c.getString(4));p.price=c.isNull(5)?0:c.getDouble(5);p.qty=c.isNull(6)?0:c.getDouble(6);p.warehouse=s(c.getString(7));p.series=s(c.getString(8));out.add(p);
            }
        }
        return out;
    }

    private String s(String x){return x==null?"":x;}
}

package com.worktruck.catalog;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;

public class Database {
    private final Context context;
    private SQLiteDatabase db;
    private static final String CLASSIFIER_VERSION="name-rules-v2";

    public static final String[] CATEGORIES={
        "Двигатель","КПП и сцепление","Шасси и рама","Кабина и кузов","Крепёж","Электрика",
        "Тормозная система","Топливная и выхлоп","Охлаждение и климат","Прочее"
    };

    public Database(Context c){context=c;}

    public synchronized void open() throws IOException{
        File target=new File(context.getFilesDir(),"products.db");
        boolean copy=!target.exists()||target.length()<1000000;
        if(!copy){
            SQLiteDatabase test=null;
            try{
                test=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
                try(Cursor c=test.rawQuery("SELECT name,qty FROM products LIMIT 1",null)){copy=!c.moveToFirst();}
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
        db=SQLiteDatabase.openDatabase(target.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
        ensureCategoryColumn();
        ensureNameClassificationFast();
    }

    private void ensureCategoryColumn(){
        boolean has=false;
        try(Cursor c=db.rawQuery("PRAGMA table_info(products)",null)){
            while(c.moveToNext())if("category".equalsIgnoreCase(c.getString(1))){has=true;break;}
        }
        if(!has)db.execSQL("ALTER TABLE products ADD COLUMN category TEXT");
    }

    private void ensureNameClassificationFast(){
        db.execSQL("CREATE TABLE IF NOT EXISTS app_meta(k TEXT PRIMARY KEY,v TEXT)");
        String current=null;
        try(Cursor c=db.rawQuery("SELECT v FROM app_meta WHERE k='classifier_version'",null)){if(c.moveToFirst())current=c.getString(0);}
        if(CLASSIFIER_VERSION.equals(current))return;

        // В V5.3 база уже поставляется с готовой категоризацией всех 94 851 позиций.
        // Проверяем это быстрым COUNT вместо тяжёлого UPDATE всей таблицы.
        int total=0, classified=0;
        try(Cursor c=db.rawQuery("SELECT count(*),sum(CASE WHEN category IS NOT NULL AND trim(category)<>'' THEN 1 ELSE 0 END) FROM products",null)){
            if(c.moveToFirst()){total=c.getInt(0);classified=c.isNull(1)?0:c.getInt(1);}
        }
        if(total>0 && classified>=total){
            db.execSQL("INSERT OR REPLACE INTO app_meta(k,v) VALUES('classifier_version',?)",new Object[]{CLASSIFIER_VERSION});
            return;
        }
        classifyAllByName();
        db.execSQL("INSERT OR REPLACE INTO app_meta(k,v) VALUES('classifier_version',?)",new Object[]{CLASSIFIER_VERSION});
    }

    public synchronized void classifyAllByName(){
        if(db==null||!db.isOpen())return;
        String n="lower(coalesce(name,''))";
        String sql="UPDATE products SET category=CASE "+
            "WHEN "+likeAny(n,"двигат","порш","коленвал","распредвал","гбц","головк блока","клапан","шатун","вкладыш","маховик","турбин","маслян","масляный насос","поддон","компрессор двиг")+" THEN 'Двигатель' "+
            "WHEN "+likeAny(n,"кпп","коробк передач","сцеплен","синхрониз","делител","ретардер","шестерн кпп","вал кпп","картер кпп","вилка сцеп")+" THEN 'КПП и сцепление' "+
            "WHEN "+likeAny(n,"тормоз","суппорт","колодк","тормозн диск","энергоаккум","тормозная камер","модулятор ebs","абс","abs")+" THEN 'Тормозная система' "+
            "WHEN "+likeAny(n,"кабин","двер","бампер","капот","крыло","зеркал","стекл","сиден","панел","спальн","обшивк","решетк радиатор","подножк","замок двери","ручка двери")+" THEN 'Кабина и кузов' "+
            "WHEN "+likeAny(n,"болт","гайк","шайб","винт","шпильк","заклеп","клипс","хомут","фиксатор","скоб","кронштейн креп","саморез")+" THEN 'Крепёж' "+
            "WHEN "+likeAny(n,"датчик","провод","жгут","реле","предохран","ламп","фонар","фара","стартер","генератор","электр","блок управ","переключател","выключател","моторчик","разъем","разъём","кнопк","антенн","аккумулятор")+" THEN 'Электрика' "+
            "WHEN "+likeAny(n,"форсунк","топлив","бак ","бак)","глушител","выхлоп","adblue","egr","scr","катализ","сажев","тнвд","топливный насос")+" THEN 'Топливная и выхлоп' "+
            "WHEN "+likeAny(n,"радиатор","интеркулер","вентилятор","кондиционер","отопител","печк","охлажден","термостат","антифриз","патрубок охлаж","компрессор кондиц","испарител","осушител кондиц")+" THEN 'Охлаждение и климат' "+
            "WHEN "+likeAny(n,"рама","балка","мост","ступиц","полуось","кардан","редуктор","рессор","амортиз","пневмобал","пневмоподуш","стабилиз","рулев","тяга","ось ","рычаг","подвеск","сайлентблок","шкворень","кулак поворот")+" THEN 'Шасси и рама' "+
            "ELSE 'Прочее' END";
        db.beginTransaction();
        try{db.execSQL(sql);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }

    private String likeAny(String field,String... keys){
        StringBuilder b=new StringBuilder("(");
        for(int i=0;i<keys.length;i++){
            if(i>0)b.append(" OR ");
            String k=keys[i].replace("'","''").toLowerCase(Locale.ROOT);
            b.append(field).append(" LIKE '%").append(k).append("%'");
        }
        return b.append(")").toString();
    }

    public synchronized int getProductCount(){
        if(db==null||!db.isOpen())return 0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products WHERE qty>0",null)){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}
    }

    public synchronized Map<String,Integer> getCategoryCounts(){
        LinkedHashMap<String,Integer> out=new LinkedHashMap<>();for(String c:CATEGORIES)out.put(c,0);
        if(db==null||!db.isOpen())return out;
        try(Cursor c=db.rawQuery("SELECT category,count(*) FROM products WHERE qty>0 GROUP BY category",null)){
            while(c.moveToNext())out.put(s(c.getString(0)),c.getInt(1));
        }catch(Exception ignored){}
        return out;
    }

    public synchronized int countMatches(String category,String query,String warehouse){
        if(db==null||!db.isOpen())return 0;QueryParts qp=buildWhere(category,query,warehouse);
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products WHERE "+qp.where,qp.args.toArray(new String[0]))){return c.moveToFirst()?c.getInt(0):0;}catch(Exception e){return 0;}
    }

    public synchronized List<Product> search(String query,String warehouse){return searchCategoryPage(null,query,warehouse,300,0);}
    public synchronized List<Product> searchCategory(String category,String query,String warehouse){return searchCategoryPage(category,query,warehouse,300,0);}

    public synchronized List<Product> searchCategoryPage(String category,String query,String warehouse,int limit,int offset){
        List<Product> out=new ArrayList<>();if(db==null||!db.isOpen())return out;QueryParts qp=buildWhere(category,query,warehouse);
        String sql="SELECT id,article,manufacturer_no,manufacturer,name,price,qty,warehouse,series FROM products WHERE "+qp.where+" ORDER BY name COLLATE NOCASE,article LIMIT "+Math.max(1,limit)+" OFFSET "+Math.max(0,offset);
        try(Cursor c=db.rawQuery(sql,qp.args.toArray(new String[0]))){
            while(c.moveToNext()){
                Product p=new Product();p.id=c.getLong(0);p.article=s(c.getString(1));p.manufacturerNo=s(c.getString(2));p.manufacturer=s(c.getString(3));p.brand=p.manufacturer;p.name=s(c.getString(4));p.price=c.isNull(5)?0:c.getDouble(5);p.qty=c.isNull(6)?0:c.getDouble(6);p.warehouse=s(c.getString(7));p.series=s(c.getString(8));out.add(p);
            }
        }
        return out;
    }

    private QueryParts buildWhere(String category,String query,String warehouse){
        String raw=query==null?"":query.trim();StringBuilder where=new StringBuilder("qty>0");ArrayList<String> args=new ArrayList<>();
        if(category!=null&&!category.trim().isEmpty()){where.append(" AND category=?");args.add(category);}
        if(!raw.isEmpty()){
            where.append(" AND (article LIKE ? COLLATE NOCASE OR manufacturer_no LIKE ? COLLATE NOCASE OR manufacturer LIKE ? COLLATE NOCASE OR name LIKE ? COLLATE NOCASE OR series LIKE ? COLLATE NOCASE)");
            args.add(raw+"%");args.add(raw+"%");args.add("%"+raw+"%");args.add("%"+raw+"%");args.add("%"+raw+"%");
        }
        if(warehouse!=null&&!warehouse.isEmpty()&&!warehouse.equals("Все склады")){where.append(" AND warehouse=?");args.add(warehouse);}
        return new QueryParts(where.toString(),args);
    }

    private static class QueryParts{final String where;final ArrayList<String> args;QueryParts(String w,ArrayList<String>a){where=w;args=a;}}
    private String s(String x){return x==null?"":x;}
}

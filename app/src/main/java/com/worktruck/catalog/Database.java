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
        File target = new File(context.getFilesDir(), "products.db");
        boolean mustCopy = !target.exists() || target.length() < 1000000;
        if (!mustCopy) {
            SQLiteDatabase test = null;
            try {
                test = SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                try (Cursor c = test.rawQuery("SELECT article,name,qty FROM products LIMIT 1", null)) { mustCopy = !c.moveToFirst(); }
            } catch (Exception e) { mustCopy = true; }
            finally { if (test != null) try { test.close(); } catch (Exception ignored) {} }
        }
        if (mustCopy) {
            File tmp = new File(context.getFilesDir(), "products.db.tmp");
            try(InputStream in=context.getAssets().open("products.db"); OutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[1024*1024]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); out.flush();
            }
            if (target.exists() && !target.delete()) throw new IOException("Не удалось обновить локальную базу");
            if (!tmp.renameTo(target)) throw new IOException("Не удалось установить базу номенклатуры");
        }
        if (db != null && db.isOpen()) db.close();
        db=SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    public synchronized int getProductCount(){
        if(db==null || !db.isOpen()) return 0;
        try(Cursor c=db.rawQuery("SELECT count(*) FROM products WHERE qty>0",null)){ return c.moveToFirst()?c.getInt(0):0; }
        catch(Exception e){ return 0; }
    }

    public synchronized Map<String,Integer> getCategoryCounts(){
        LinkedHashMap<String,Integer> result=new LinkedHashMap<>();
        for(String c:CATEGORIES) result.put(c,0);
        if(db==null || !db.isOpen()) return result;
        String sql="SELECT "+categoryCase()+" AS cat, count(*) FROM products WHERE qty>0 GROUP BY cat";
        try(Cursor c=db.rawQuery(sql,null)){
            while(c.moveToNext()) result.put(s(c.getString(0)),c.getInt(1));
        } catch(Exception ignored){}
        return result;
    }

    public synchronized List<Product> search(String query,String warehouse){ return searchCategory(null,query,warehouse); }

    public synchronized List<Product> searchCategory(String category,String query,String warehouse){
        List<Product> out=new ArrayList<>();
        if(db==null || !db.isOpen()) return out;
        String raw=query==null?"":query.trim();
        String upper=raw.toUpperCase(Locale.ROOT);
        String title=raw.isEmpty()?raw:raw.substring(0,1).toUpperCase(Locale.ROOT)+raw.substring(1).toLowerCase(Locale.ROOT);
        StringBuilder where=new StringBuilder("qty>0");
        List<String> args=new ArrayList<>();

        if(category!=null && !category.trim().isEmpty()){
            where.append(" AND (").append(categoryCase()).append(")=?");
            args.add(category);
        }
        if(!raw.isEmpty()){
            where.append(" AND (article LIKE ? OR manufacturer_no LIKE ? OR manufacturer LIKE ? OR manufacturer LIKE ? OR name LIKE ? OR name LIKE ? OR name LIKE ? OR series LIKE ? OR series LIKE ?)");
            args.add(raw+"%"); args.add(raw+"%"); args.add("%"+raw+"%"); args.add("%"+upper+"%");
            args.add("%"+raw+"%"); args.add("%"+title+"%"); args.add("%"+upper+"%"); args.add("%"+raw+"%"); args.add("%"+upper+"%");
        }
        if(warehouse!=null && !warehouse.isEmpty() && !warehouse.equals("Все склады")){
            where.append(" AND warehouse=?"); args.add(warehouse);
        }

        String sql="SELECT id,article,manufacturer_no,manufacturer,name,price,qty,warehouse,series FROM products WHERE "+where+" ORDER BY qty DESC,name LIMIT 150";
        try(Cursor c=db.rawQuery(sql,args.toArray(new String[0]))){
            while(c.moveToNext()){
                Product p=new Product(); p.id=c.getLong(0); p.article=s(c.getString(1)); p.manufacturerNo=s(c.getString(2));
                p.manufacturer=s(c.getString(3)); p.brand=p.manufacturer; p.name=s(c.getString(4));
                p.price=c.isNull(5)?0:c.getDouble(5); p.qty=c.isNull(6)?0:c.getDouble(6); p.warehouse=s(c.getString(7)); p.series=s(c.getString(8)); out.add(p);
            }
        }
        return out;
    }

    private String categoryCase(){
        return "CASE "+
            "WHEN "+m("двигат","Двигат","ДВИГАТ","порш","Порш","коленвал","Коленвал","распредвал","Распредвал","головк блока","Головк блока","ГБЦ","клапан","Клапан","шатун","Шатун","вкладыш","Вкладыш","маховик","Маховик","турбин","Турбин")+" THEN 'Двигатель' "+
            "WHEN "+m("КПП","кпп","коробк передач","Коробк передач","сцеплен","Сцеплен","синхрониз","Синхрониз","делител","Делител","ретардер","Ретардер","шестерн","Шестерн")+" THEN 'КПП и сцепление' "+
            "WHEN "+m("тормоз","Тормоз","суппорт","Суппорт","колодк","Колодк","тормозн диск","Тормозн диск","энергоаккум","Энергоаккум")+" THEN 'Тормозная система' "+
            "WHEN "+m("кабин","Кабин","двер","Двер","бампер","Бампер","капот","Капот","крыло","Крыло","зеркал","Зеркал","стекл","Стекл","сиден","Сиден","панел","Панел","спальн","Спальн")+" THEN 'Кабина и кузов' "+
            "WHEN "+m("болт","Болт","гайк","Гайк","шайб","Шайб","винт","Винт","шпильк","Шпильк","заклеп","Заклеп","клипс","Клипс","хомут","Хомут","фиксатор","Фиксатор")+" THEN 'Крепёж' "+
            "WHEN "+m("датчик","Датчик","провод","Провод","жгут","Жгут","реле","Реле","предохран","Предохран","ламп","Ламп","фонар","Фонар","фара","Фара","стартер","Стартер","генератор","Генератор","электр","Электр","блок управ","Блок управ","переключател","Переключател","выключател","Выключател")+" THEN 'Электрика' "+
            "WHEN "+m("форсунк","Форсунк","топлив","Топлив","бак ","Бак ","глушител","Глушител","выхлоп","Выхлоп","AdBlue","ADBLUE","EGR","SCR","катализ","Катализ")+" THEN 'Топливная и выхлоп' "+
            "WHEN "+m("радиатор","Радиатор","интеркулер","Интеркулер","вентилятор","Вентилятор","кондиционер","Кондиционер","отопител","Отопител","печк","Печк","охлажден","Охлажден")+" THEN 'Охлаждение и климат' "+
            "WHEN "+m("рама","Рама","балка","Балка","мост","Мост","ступиц","Ступиц","полуось","Полуось","кардан","Кардан","редуктор","Редуктор","рессор","Рессор","амортиз","Амортиз","пневмобал","Пневмобал","стабилиз","Стабилиз","рулев","Рулев","тяга","Тяга","ось ","Ось ")+" THEN 'Шасси и рама' "+
            "ELSE 'Прочее' END";
    }

    private String m(String... keys){
        String field="(coalesce(name,'')||' '||coalesce(series,'')||' '||coalesce(manufacturer,''))";
        StringBuilder b=new StringBuilder("(");
        for(int i=0;i<keys.length;i++){
            if(i>0)b.append(" OR ");
            b.append(field).append(" LIKE '%").append(keys[i].replace("'","''")).append("%'");
        }
        return b.append(")").toString();
    }

    private String s(String value){ return value==null?"":value; }
}

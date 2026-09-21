from pathlib import Path
import re

BLUE_ACCENT='Color.rgb(18,92,185)'
BLUE_DARK='Color.rgb(8,39,79)'

# V6.8: one clear navigation model.
# Bottom bar = Home / Catalog / Request / SOS / More.
# Home feed contains only content that is NOT duplicated in the bottom bar.
p=Path('app/src/main/java/com/worktruck/catalog/V53Activity.java')
s=p.read_text(encoding='utf-8')

# Corporate dark-blue palette.
s=s.replace('Color.rgb(8,91,58)',BLUE_ACCENT)
s=s.replace('Color.rgb(7,38,29)',BLUE_DARK)

# Faster start and deep links from More.
s=s.replace('postDelayed(this::showHome,550)','postDelayed(this::openStartScreen,420)')
if 'private void openStartScreen()' not in s:
    marker='    private void showSplash(){'
    helper='''    private void openStartScreen(){
        String screen=getIntent().getStringExtra("screen");
        if("request".equals(screen)){showRequest("");return;}
        if("orders".equals(screen)){showOrders();return;}
        if("profile".equals(screen)){showProfile();return;}
        showHome();
    }

'''
    if marker not in s: raise SystemExit('showSplash marker not found')
    s=s.replace(marker,helper+marker,1)

home='''    private void showHome(){
        prepare(0);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        LinearLayout b=col();b.setPadding(dp(14),dp(10),dp(14),dp(18));
        b.addView(header());

        EditText search=searchField("Поиск по всей номенклатуре");
        search.setOnEditorActionListener((v,id,e)->{showProducts(null,search.getText().toString().trim());return true;});
        b.addView(search,lp(-1,dp(52),0,8,0,12));

        TextView lead=tv("Главное",21,DARK,true);lead.setPadding(dp(2),0,0,dp(8));b.addView(lead);
        b.addView(homeEntry("МОИ МАШИНЫ","VIN, госномера и сохранённые Scania","▣",()->{Intent i=new Intent(this,ClientHubActivity.class);i.putExtra("section","fleet");startActivity(i);}));
        b.addView(homeEntry("КЛИЕНТУ","Бонусы, ожидание деталей, статус заказа и менеджер","●",()->startActivity(new Intent(this,ClientHubActivity.class))));
        b.addView(photoHero("НОВЫЕ ПОСТУПЛЕНИЯ","Автомобили Scania в разбор и новые позиции","Смотреть поступления",this::showVehicles),lp(-1,dp(168),0,10,0,0));

        LinearLayout info=card();
        info.addView(tv(dbReady?"Каталог готов":"База номенклатуры недоступна",15,dbReady?GREEN:Color.rgb(170,80,0),true));
        info.addView(tv(dbReady?("В наличии: "+nf.format(database.getProductCount())+" позиций"):"Закройте приложение и откройте снова.",13,MUTED,false));
        b.addView(info,lp(-1,-2,0,14,0,0));

        sv.addView(b);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));root.addView(bottomNav());
    }

'''
pattern=r'    private void showHome\(\)\{.*?\n    private void showVehicles\(\)\{'
if not re.search(pattern,s,flags=re.S): raise SystemExit('showHome block not found')
s=re.sub(pattern,home+'    private void showVehicles(){',s,count=1,flags=re.S)

# Compact home entry: clickable whole card, no hidden CTA button.
if 'private View homeEntry(' not in s:
    marker='    private View header(){'
    helper='''    private View homeEntry(String title,String sub,String icon,Runnable click){
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(15),dp(12),dp(13),dp(12));
        TextView ic=tv(icon,22,GREEN,true);ic.setGravity(Gravity.CENTER);ic.setBackground(roundRect(Color.rgb(232,241,253),14));c.addView(ic,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout tx=col();tx.setPadding(dp(12),0,dp(5),0);tx.addView(tv(title,17,DARK,true));TextView st=tv(sub,12,MUTED,false);st.setPadding(0,dp(3),0,0);tx.addView(st);c.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView ar=tv("›",28,GREEN,true);ar.setGravity(Gravity.CENTER);c.addView(ar,new LinearLayout.LayoutParams(dp(28),dp(52)));
        c.setOnClickListener(v->click.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(9));c.setLayoutParams(p);return c;
    }

'''
    if marker not in s: raise SystemExit('header marker not found')
    s=s.replace(marker,helper+marker,1)

# Replace the bottom navigation completely: no duplicate Orders/Profile/Tour entries.
nav='''    private View bottomNav(){
        LinearLayout n=row();n.setPadding(dp(4),dp(5),dp(4),dp(5));n.setBackgroundColor(Color.WHITE);
        n.addView(nav("⌂","Главная",0,this::showHome),weight());
        n.addView(nav("▦","Каталог",1,this::showCategories),weight());
        n.addView(nav("+","Заявка",2,()->showRequest("")),weight());
        n.addView(sosNav(),weight());
        n.addView(nav("⋯","Ещё",4,()->startActivity(new Intent(this,MoreActivity.class))),weight());
        return n;
    }
    private View sosNav(){
        final int RED=Color.rgb(201,43,43);
        LinearLayout b=col();b.setGravity(Gravity.CENTER);
        TextView i=tv("SOS",11,Color.WHITE,true);i.setGravity(Gravity.CENTER);i.setBackground(roundRect(RED,16));i.setPadding(dp(9),dp(5),dp(9),dp(5));
        TextView t=tv("SOS",10,RED,true);t.setGravity(Gravity.CENTER);t.setPadding(0,dp(2),0,0);
        b.addView(i);b.addView(t);b.setOnClickListener(v->startActivity(new Intent(this,SosActivity.class)));return b;
    }
    private View nav(String icon,String text,int idx,Runnable r){
        LinearLayout b=col();b.setGravity(Gravity.CENTER);
        TextView i=tv(icon,17,idx==activeTab?GREEN:MUTED,true);i.setGravity(Gravity.CENTER);
        TextView t=tv(text,10,idx==activeTab?GREEN:MUTED,idx==activeTab);t.setGravity(Gravity.CENTER);
        b.addView(i);b.addView(t);b.setOnClickListener(v->r.run());return b;
    }
'''
nav_pattern=r'    private View bottomNav\(\)\{.*?\n    private View titleBack'
if not re.search(nav_pattern,s,flags=re.S): raise SystemExit('bottomNav block not found')
s=re.sub(nav_pattern,nav+'    private View titleBack',s,count=1,flags=re.S)

# Orders/Profile are now reached from More, so the More tab is the contextual parent.
s=s.replace('private void showOrders(){prepare(3);','private void showOrders(){prepare(4);')
# showProfile already uses tab 4.

p.write_text(s,encoding='utf-8')

# Client hub: My cars exists on Home only, not duplicated inside Client.
cp=Path('app/src/main/java/com/worktruck/catalog/ClientHubActivity.java')
c=cp.read_text(encoding='utf-8')
c=c.replace('Color.rgb(8,91,58)',BLUE_ACCENT).replace('Color.rgb(7,38,29)',BLUE_DARK)
if 'private boolean directSection=false;' not in c:
    c=c.replace('    private LinearLayout root;','    private LinearLayout root;\n    private boolean directSection=false;',1)
c=c.replace('        prefs=getSharedPreferences("wt_client_hub",MODE_PRIVATE);\n        showHub();',
'''        prefs=getSharedPreferences("wt_client_hub",MODE_PRIVATE);
        String section=getIntent().getStringExtra("section");
        directSection=section!=null&&!section.isEmpty();
        if("fleet".equals(section))showFleet();
        else if("bonus".equals(section))showBonus();
        else if("watch".equals(section))showWatch();
        else if("orders".equals(section))showOrderStatus();
        else if("manager".equals(section))showManager();
        else {directSection=false;showHub();}''',1)
if 'private void backFromSection()' not in c:
    c=c.replace('    private void showHub(){','    private void backFromSection(){if(directSection)finish();else showHub();}\n\n    private void showHub(){',1)
c=c.replace('        body.addView(menuCard("🚛","Мой автопарк","Сохраните свои Scania: модель, VIN и госномер",this::showFleet));\n','')
c=c.replace('Пять быстрых сервисов для постоянного клиента. Сейчас часть функций работает локально; после подключения ERP данные станут персональными и обновляемыми автоматически.',
            'Сервисы для постоянного клиента. После подключения ERP данные станут персональными и будут обновляться автоматически.')
for a,b in [
('base("Мой автопарк",this::showHub);','base("Мой автопарк",this::backFromSection);'),
('base("Бонусный кошелёк",this::showHub);','base("Бонусный кошелёк",this::backFromSection);'),
('base("Сообщить о поступлении",this::showHub);','base("Сообщить о поступлении",this::backFromSection);'),
('base("Где мой заказ?",this::showHub);','base("Где мой заказ?",this::backFromSection);'),
('base("Персональный менеджер",this::showHub);','base("Персональный менеджер",this::backFromSection);')
]: c=c.replace(a,b)
c=c.replace('@Override public void onBackPressed(){showHub();}','@Override public void onBackPressed(){if(directSection)finish();else showHub();}')
cp.write_text(c,encoding='utf-8')

# Corporate blue on legacy auxiliary screens.
for rel in ['app/src/main/java/com/worktruck/catalog/GameActivity.java','app/src/main/java/com/worktruck/catalog/TourActivity.java','app/src/main/java/com/worktruck/catalog/MoreActivity.java']:
    q=Path(rel)
    if q.exists():
        t=q.read_text(encoding='utf-8').replace('Color.rgb(8,91,58)',BLUE_ACCENT).replace('Color.rgb(7,38,29)',BLUE_DARK)
        q.write_text(t,encoding='utf-8')

# Build assertions for the interface the user will actually see.
out=p.read_text(encoding='utf-8')
home_slice=out[out.index('private void showHome()'):out.index('private void showVehicles()')]
assert 'МОИ МАШИНЫ' in home_slice
assert 'КЛИЕНТУ' in home_slice
assert 'НОВЫЕ ПОСТУПЛЕНИЯ' in home_slice
assert 'hero("КАТАЛОГ"' not in home_slice
assert 'hero("ЕЩЁ"' not in home_slice
assert 'nav("▦","Каталог"' in out
assert 'nav("+","Заявка"' in out
assert 'sosNav()' in out
assert 'nav("⋯","Ещё"' in out
assert 'nav("☷","Заказы"' not in out
assert 'nav("●","Профиль"' not in out
cout=cp.read_text(encoding='utf-8')
assert '"fleet".equals(section)' in cout
assert 'menuCard("🚛","Мой автопарк"' not in cout
print('V6.8 DEDUPLICATED CLIENT NAV + RED SOS CONFIRMED')

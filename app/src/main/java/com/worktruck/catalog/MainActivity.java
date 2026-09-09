package com.worktruck.catalog;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(8, 91, 58);
    private static final int DARK = Color.rgb(9, 44, 33);
    private static final int BG = Color.rgb(246, 248, 247);
    private static final int MUTED = Color.rgb(106, 116, 110);
    private Database database;
    private LinearLayout root;
    private int activeTab = 0;
    private final String[] warehouses = {"Все склады", "Нижний Новгород", "Москва", "Екатеринбург"};
    private final ArrayList<String> localOrders = new ArrayList<>();
    private NumberFormat nf;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        nf = NumberFormat.getNumberInstance(new Locale("ru","RU"));
        nf.setMaximumFractionDigits(0);
        database = new Database(this);
        try { database.open(); } catch(Exception e) { Toast.makeText(this, "Ошибка базы номенклатуры: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        loadOrders();
        showSplash();
    }

    private void showSplash() {
        LinearLayout splash = col();
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(DARK);
        splash.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand_logo);
        logo.setBackgroundResource(R.drawable.shape_logo_bg);
        logo.setPadding(dp(26), dp(26), dp(26), dp(26));
        splash.addView(logo, new LinearLayout.LayoutParams(dp(190), dp(190)));

        TextView title = tv("Ворк Трак", 34, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(20), 0, 0);
        splash.addView(title);

        TextView sub = tv("ЗАПЧАСТИ • СЕРВИС • НАДЁЖНЫЕ РЕШЕНИЯ", 12, Color.rgb(204, 223, 215), false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, 0);
        splash.addView(sub);

        TextView tag = tv("Запчасти для Scania и больших возможностей", 14, Color.WHITE, false);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, dp(42), 0, 0);
        splash.addView(tag);

        setContentView(splash);
        new Handler(Looper.getMainLooper()).postDelayed(this::showHome, 1100);
    }

    private void prepareRoot(int tab) {
        activeTab = tab;
        root = col();
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private void showHome() {
        prepareRoot(0);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = col();
        body.setPadding(dp(16), dp(12), dp(16), dp(16));

        body.addView(homeHeader());

        EditText search = searchField("Поиск запчасти");
        search.setOnEditorActionListener((v, id, e) -> { String q = search.getText().toString().trim(); showCatalog(q); return true; });
        body.addView(search, lp(-1, dp(54), 0, 8, 0, 0));

        body.addView(featureRow());
        body.addView(heroBanner("НОВЫЕ ПОСТУПЛЕНИЯ", "Оригинальные и аналоговые запчасти для Scania", "Перейти в каталог", () -> showCatalog(""), false), lp(-1, dp(154), 0, 12, 0, 0));
        body.addView(heroBanner("МАШИНЫ В РАЗБОР", "Проверенные Scania. Новые позиции каждую неделю", "Смотреть наличие", () -> showCatalog("scania"), true), lp(-1, dp(134), 0, 12, 0, 0));

        body.addView(sectionTitle("Быстрые действия"));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.addView(actionCard("⌕", "Найти по артикулу", () -> showCatalog("")), gridLp());
        grid.addView(actionCard("VIN", "Отправить VIN", () -> showRequest("")), gridLp());
        grid.addView(actionCard("▣", "Загрузить фото", () -> showRequest("")), gridLp());
        grid.addView(actionCard("☎", "Связаться с менеджером", () -> dial("88005509638")), gridLp());
        body.addView(grid);

        body.addView(sectionTitle("Популярные категории"));
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout cats = row();
        cats.addView(categoryChip("Двигатель", "двигатель"));
        cats.addView(categoryChip("Трансмиссия", "трансмиссия"));
        cats.addView(categoryChip("Подвеска", "подвеска"));
        cats.addView(categoryChip("Тормоза", "тормоз"));
        hsv.addView(cats);
        body.addView(hsv);

        body.addView(infoStrip());
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());
    }

    private View homeHeader() {
        LinearLayout h = row();
        h.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.brand_logo);
        icon.setBackgroundResource(R.drawable.shape_logo_bg);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        h.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout tt = col();
        tt.setPadding(dp(12), 0, 0, 0);
        tt.addView(tv("Ворк Трак", 23, DARK, true));
        tt.addView(tv("Москва", 12, MUTED, false));
        h.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        TextView bell = tv("◌", 18, GREEN, true);
        bell.setGravity(Gravity.CENTER);
        h.addView(bell, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return h;
    }

    private View featureRow() {
        LinearLayout row = this.row();
        row.setPadding(0, dp(4), 0, 0);
        row.addView(featureItem("◈", "Оригинальное\nкачество"), weightLp(1));
        row.addView(featureItem("⚙", "Профессиональный\nподбор"), weightLp(1));
        row.addView(featureItem("⇄", "Надёжный\nпартнёр"), weightLp(1));
        return row;
    }

    private void showCatalog(String initialQuery) {
        prepareRoot(1);
        LinearLayout header = col();
        header.setPadding(dp(16), dp(12), dp(16), dp(10));
        header.setBackgroundColor(Color.WHITE);
        header.addView(titleWithBack("Каталог", this::showHome));

        EditText search = searchField("Артикул, номер производителя или название");
        search.setText(initialQuery);
        header.addView(search, lp(-1, dp(54), 0, 4, 0, 0));

        Spinner wh = new Spinner(this);
        ArrayAdapter<String> wa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, warehouses);
        wh.setAdapter(wa);
        header.addView(wh, lp(-1, dp(48), 0, 10, 0, 0));

        root.addView(header);

        TextView count = tv("", 13, MUTED, false);
        count.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.addView(count);

        ListView list = new ListView(this);
        list.setDividerHeight(0);
        list.setPadding(dp(8), 0, dp(8), dp(4));
        ProductAdapter adapter = new ProductAdapter(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());

        Runnable doSearch = () -> {
            String q = search.getText().toString();
            String w = String.valueOf(wh.getSelectedItem());
            count.setText("Ищем по базе…");
            new Thread(() -> {
                List<Product> data = database.search(q, w);
                runOnUiThread(() -> {
                    adapter.set(data);
                    count.setText("Найдено: " + data.size() + " · показываем до 150 позиций");
                });
            }).start();
        };
        search.setOnEditorActionListener((v, id, e) -> { doSearch.run(); return true; });
        wh.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { doSearch.run(); }
        });
        list.setOnItemClickListener((p, v, pos, id) -> showProduct(adapter.items.get(pos)));
        doSearch.run();
    }

    private void showRequest(String article) {
        prepareRoot(2);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = col();
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        body.addView(titleWithBack("Заявка на запчасти", this::showHome));

        TextView help = tv("Оставьте данные, и мы подберём нужные детали для вашего Scania.", 14, MUTED, false);
        help.setPadding(0, 0, 0, dp(14));
        body.addView(help);

        body.addView(label("VIN номер"));
        EditText vin = searchField("Например: YS2R4X20005312345");
        body.addView(vin, lp(-1, dp(54), 0, 0, 0, 12));

        body.addView(label("Артикул запчасти"));
        EditText art = searchField("Например: 1861567");
        art.setText(article);
        body.addView(art, lp(-1, dp(54), 0, 0, 0, 12));

        body.addView(label("Фото детали"));
        Button photo = greenButton("ДОБАВИТЬ ФОТО");
        photo.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, 77);
            } catch(Exception e) {
                Toast.makeText(this, "Открыть галерею не удалось", Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(photo, lp(-1, dp(52), 0, 0, 0, 12));

        body.addView(label("Комментарий"));
        EditText note = new EditText(this);
        note.setHint("Укажите дополнительную информацию: состояние, аналог, срочность и т.д.");
        note.setTextSize(15);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setMinLines(5);
        note.setGravity(Gravity.TOP);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setBackgroundResource(R.drawable.shape_search);
        body.addView(note, lp(-1, dp(140), 0, 0, 0, 16));

        Button send = greenButton("ОТПРАВИТЬ ЗАЯВКУ");
        body.addView(send, lp(-1, dp(56), 0, 0, 0, 8));
        send.setOnClickListener(v -> {
            String a = art.getText().toString().trim();
            String vno = vin.getText().toString().trim();
            String n = note.getText().toString().trim();
            String id = new SimpleDateFormat("ddMMyy-HHmm", Locale.getDefault()).format(new Date());
            String item = "#" + id + "|" + (a.isEmpty() ? "Без артикула" : a) + "|В работе|" + (vno.isEmpty() ? "VIN не указан" : vno) + "|" + n;
            localOrders.add(0, item);
            saveOrders();
            new AlertDialog.Builder(this)
                    .setTitle("Заявка принята")
                    .setMessage("Тестовая заявка сохранена в приложении и отображается в разделе «Мои заявки».\n\nСледующий этап — подключение CRM / Telegram / API для реальной отправки менеджеру.")
                    .setPositiveButton("Мои заявки", (d, x) -> showOrders())
                    .setNegativeButton("На главную", (d, x) -> showHome())
                    .show();
        });

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());
    }

    private void showOrders() {
        prepareRoot(3);
        LinearLayout body = col();
        body.setPadding(dp(16), dp(14), dp(16), dp(10));
        body.addView(titleWithBack("Мои заявки", this::showHome));

        if(localOrders.isEmpty()) {
            LinearLayout emptyWrap = col();
            emptyWrap.setGravity(Gravity.CENTER);
            TextView empty = tv("Пока заявок нет", 19, DARK, true);
            TextView sub = tv("Создайте первую заявку на запчасть — она появится здесь.", 14, MUTED, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(dp(10), dp(8), dp(10), 0);
            emptyWrap.addView(empty);
            emptyWrap.addView(sub);
            body.addView(emptyWrap, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ScrollView s = new ScrollView(this);
            LinearLayout cards = col();
            for(String raw : localOrders){
                String[] p = raw.split("\\|", -1);
                LinearLayout card = card();
                LinearLayout top = row();
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(tv(p.length > 0 ? p[0] : "Заявка", 16, DARK, true), new LinearLayout.LayoutParams(0, -2, 1));
                TextView status = chip(p.length > 2 ? p[2] : "В работе", R.drawable.shape_status_orange, Color.rgb(201,115,0));
                top.addView(status);
                card.addView(top);
                card.addView(tv("Артикул: " + (p.length > 1 ? p[1] : "—"), 14, DARK, false));
                card.addView(tv(p.length > 3 ? p[3] : "", 13, MUTED, false));
                cards.addView(card, lp(-1, -2, 0, 0, 0, 10));
            }
            s.addView(cards);
            body.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());
    }

    private void showProfile() {
        prepareRoot(4);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = col();
        body.setPadding(dp(16), dp(14), dp(16), dp(22));
        body.addView(titleWithBack("Профиль", this::showHome));

        LinearLayout person = card();
        person.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.brand_logo);
        icon.setBackgroundResource(R.drawable.shape_logo_bg);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        person.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout text = col(); text.setPadding(dp(12), 0, 0, 0);
        text.addView(tv("Личный кабинет", 18, DARK, true));
        text.addView(tv("Клиент Ворк Трак", 13, MUTED, false));
        person.addView(text);
        body.addView(person, lp(-1, -2, 0, 0, 0, 12));

        LinearLayout bonus = card();
        bonus.setBackgroundResource(R.drawable.shape_bonus);
        bonus.addView(tv("Бонусный баланс", 14, Color.WHITE, false));
        bonus.addView(tv("12 450 ₽", 26, Color.WHITE, true));
        body.addView(bonus, lp(-1, -2, 0, 0, 0, 12));

        body.addView(sectionTitle("Ваш менеджер"));
        LinearLayout mgr = card();
        mgr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout mtext = col();
        mtext.addView(tv("Алексей Сорокин", 16, DARK, true));
        mtext.addView(tv("+7 (916) 123-45-67", 13, MUTED, false));
        mgr.addView(mtext, new LinearLayout.LayoutParams(0, -2, 1));
        Button call = greenButton("Позвонить");
        call.setOnClickListener(v -> dial("79161234567"));
        mgr.addView(call, new LinearLayout.LayoutParams(dp(118), dp(46)));
        body.addView(mgr, lp(-1, -2, 0, 0, 0, 12));

        body.addView(sectionTitle("Ваш филиал"));
        LinearLayout branch = card();
        branch.addView(tv("Москва", 16, DARK, true));
        branch.addView(tv("ул. Дорожная, 12, стр. 1", 13, MUTED, false));
        branch.addView(tv("Пн–Пт 9:00–18:00", 13, MUTED, false));
        branch.addView(tv("8 800 550-96-38", 13, MUTED, false));
        body.addView(branch, lp(-1, -2, 0, 0, 0, 12));

        body.addView(sectionTitle("Статистика"));
        LinearLayout stats = row();
        stats.addView(statBox(String.valueOf(localOrders.size()), "Заявок"), weightLp(1));
        stats.addView(space(dp(8), 1));
        stats.addView(statBox(String.valueOf(database.getProductCount()), "Позиций"), weightLp(1));
        body.addView(stats);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomNav());
    }

    private View infoStrip() {
        LinearLayout box = card();
        TextView main = tv("В приложении сейчас: " + database.getProductCount() + " позиций с наличием", 14, DARK, true);
        TextView sub = tv("Поиск работает по артикулу, номеру производителя, названию и складу.", 13, MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        box.addView(main); box.addView(sub);
        return box;
    }

    private View bottomNav() {
        LinearLayout nav = row();
        nav.setBackgroundResource(R.drawable.shape_bottom_nav);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.addView(navItem("⌂", "Главная", 0, this::showHome), weightLp(1));
        nav.addView(navItem("⌕", "Каталог", 1, () -> showCatalog("")), weightLp(1));
        nav.addView(navItem("▣", "Заявка", 2, () -> showRequest("")), weightLp(1));
        nav.addView(navItem("☰", "Заказы", 3, this::showOrders), weightLp(1));
        nav.addView(navItem("●", "Профиль", 4, this::showProfile), weightLp(1));
        return nav;
    }

    private View navItem(String icon, String text, int index, Runnable r) {
        LinearLayout box = col();
        box.setGravity(Gravity.CENTER);
        TextView i = tv(icon, 18, index == activeTab ? GREEN : MUTED, true);
        i.setGravity(Gravity.CENTER);
        TextView t = tv(text, 11, index == activeTab ? GREEN : MUTED, index == activeTab);
        t.setGravity(Gravity.CENTER);
        box.addView(i); box.addView(t);
        box.setOnClickListener(v -> r.run());
        box.setPadding(0, dp(2), 0, dp(2));
        return box;
    }

    private View titleWithBack(String text, Runnable r) {
        LinearLayout x = row();
        x.setGravity(Gravity.CENTER_VERTICAL);
        x.setPadding(0, 0, 0, dp(10));
        TextView b = tv("‹", 32, DARK, false);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> r.run());
        x.addView(b, new LinearLayout.LayoutParams(dp(34), dp(40)));
        x.addView(tv(text, 23, DARK, true));
        return x;
    }

    private TextView sectionTitle(String s) { TextView v = tv(s, 17, DARK, true); v.setPadding(0, dp(16), 0, dp(10)); return v; }
    private TextView label(String s) { TextView v = tv(s, 14, DARK, true); v.setPadding(0, 0, 0, dp(6)); return v; }

    private EditText searchField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackgroundResource(R.drawable.shape_search);
        return e;
    }

    private Button greenButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundResource(R.drawable.shape_button_green);
        return b;
    }

    private LinearLayout heroBanner(String title, String sub, String cta, Runnable run, boolean alt) {
        LinearLayout box = col();
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        box.setBackgroundResource(alt ? R.drawable.shape_banner_alt : R.drawable.shape_banner);
        TextView t = tv(title, 22, Color.WHITE, true);
        TextView s = tv(sub, 13, Color.rgb(212, 227, 220), false);
        s.setPadding(0, dp(4), 0, dp(10));
        Button b = greenButton(cta + "  →");
        b.setOnClickListener(v -> run.run());
        box.addView(t); box.addView(s); box.addView(b, new LinearLayout.LayoutParams(dp(194), dp(42)));
        return box;
    }

    private View actionCard(String icon, String text, Runnable r) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_VERTICAL);
        TextView i = tv(icon, 19, GREEN, true);
        i.setGravity(Gravity.CENTER);
        i.setBackgroundResource(R.drawable.shape_chip);
        c.addView(i, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView t = tv(text, 13, DARK, true);
        t.setPadding(dp(10), 0, 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.setOnClickListener(v -> r.run());
        return c;
    }

    private View categoryChip(String text, String query) {
        TextView t = chip(text, R.drawable.shape_chip, DARK);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(p);
        t.setOnClickListener(v -> showCatalog(query));
        return t;
    }

    private View featureItem(String icon, String text) {
        LinearLayout v = col();
        v.setGravity(Gravity.CENTER);
        TextView i = tv(icon, 16, GREEN, true);
        i.setGravity(Gravity.CENTER);
        TextView t = tv(text, 11, DARK, true);
        t.setGravity(Gravity.CENTER);
        v.addView(i);
        v.addView(t);
        return v;
    }

    private View statBox(String n, String label) {
        LinearLayout b = card();
        b.setGravity(Gravity.CENTER);
        TextView a = tv(n, 19, GREEN, true); a.setGravity(Gravity.CENTER);
        TextView l = tv(label, 12, MUTED, false); l.setGravity(Gravity.CENTER);
        b.addView(a); b.addView(l);
        return b;
    }

    private TextView chip(String text, int bgRes, int color) {
        TextView t = tv(text, 12, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setBackgroundResource(bgRes);
        return t;
    }

    private LinearLayout card() {
        LinearLayout x = col();
        x.setPadding(dp(14), dp(13), dp(14), dp(13));
        x.setBackgroundResource(R.drawable.shape_card);
        return x;
    }

    private GridLayout.LayoutParams gridLp() {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = 0; p.height = dp(82); p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams weightLp(float weight) { return new LinearLayout.LayoutParams(0, -2, weight); }
    private View space(int width, int height) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(width, height)); return s; }
    private LinearLayout.LayoutParams lp(int w, int h, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(left, top, right, bottom); return p; }
    private LinearLayout col() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x; }
    private LinearLayout row() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); return x; }
    private TextView tv(String text, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(color); if(bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "—" : s; }
    private void dial(String number) { try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); } catch(Exception e) { Toast.makeText(this, "Номер: " + number, Toast.LENGTH_SHORT).show(); } }
    private void loadOrders() { String raw = getSharedPreferences("wt", MODE_PRIVATE).getString("orders", ""); if(!raw.isEmpty()) localOrders.addAll(Arrays.asList(raw.split("\\n§\\n"))); }
    private void saveOrders() { getSharedPreferences("wt", MODE_PRIVATE).edit().putString("orders", android.text.TextUtils.join("\n§\n", localOrders)).apply(); }

    private void showProduct(Product p) {
        String text = "Артикул: " + safe(p.article) + "\nНомер производителя: " + safe(p.manufacturerNo) + "\nПроизводитель: " + safe(p.manufacturer) +
                "\nСерия: " + safe(p.series) + "\n\nЦена: " + nf.format(p.price) + " ₽\nНаличие: " + nf.format(p.qty) + " шт.\nСклад: " + safe(p.warehouse);
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setMessage(text)
                .setPositiveButton("Создать заявку", (d, x) -> showRequest(p.article))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private class ProductAdapter extends BaseAdapter {
        private final Context c;
        private List<Product> items = new ArrayList<>();
        ProductAdapter(Context c){ this.c = c; }
        void set(List<Product> d){ items = d; notifyDataSetChanged(); }
        public int getCount(){ return items.size(); }
        public Object getItem(int p){ return items.get(p); }
        public long getItemId(int p){ return items.get(p).id; }
        public View getView(int pos, View convert, ViewGroup parent) {
            Product p = items.get(pos);
            LinearLayout box;
            if(convert == null){
                box = new LinearLayout(c);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(14), dp(12), dp(14), dp(12));
                box.setBackgroundResource(R.drawable.shape_card);
                TextView n = new TextView(c); n.setId(1001); n.setTextSize(16); n.setTextColor(DARK); n.setTypeface(Typeface.DEFAULT, Typeface.BOLD); box.addView(n);
                TextView a = new TextView(c); a.setId(1002); a.setTextSize(12); a.setTextColor(MUTED); a.setPadding(0, dp(4), 0, 0); box.addView(a);
                TextView pr = new TextView(c); pr.setId(1003); pr.setTextSize(15); pr.setTextColor(GREEN); pr.setTypeface(Typeface.DEFAULT, Typeface.BOLD); pr.setPadding(0, dp(6), 0, 0); box.addView(pr);
                AbsListView.LayoutParams lp = new AbsListView.LayoutParams(-1, -2); box.setLayoutParams(lp);
                box.setPadding(dp(14), dp(12), dp(14), dp(12));
            } else box = (LinearLayout)convert;
            ((TextView)box.findViewById(1001)).setText(p.name);
            ((TextView)box.findViewById(1002)).setText("Арт. " + safe(p.article) + " · " + safe(p.warehouse) + " · " + safe(p.series));
            ((TextView)box.findViewById(1003)).setText(nf.format(p.price) + " ₽   ·   " + nf.format(p.qty) + " шт.");
            return box;
        }
    }
}

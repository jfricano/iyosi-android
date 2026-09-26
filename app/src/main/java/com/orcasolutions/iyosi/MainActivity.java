package com.orcasolutions.iyosi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements PlaceMapView.Listener {
    private static final String[] KINDS = {"public_designated", "venue_designated", "informal_outdoor", "venue_claim"};
    private static final String[] KIND_LABELS = {"Public designated", "Venue designated", "Outdoor report", "Venue claim"};
    private static final String[] STATUSES = {"verified_designated", "community_reported", "disputed", "prohibited"};
    private static final String[] STATUS_LABELS = {"Verified", "Community report", "Disputed", "Prohibited warning"};
    private static final String[] REPORT_REASONS = {"wrong_status", "closed", "access_notes", "privacy", "harassment", "spam", "other"};
    private final boolean[] kinds = {true, true, true, true};
    private final boolean[] statuses = {true, true, true, false};
    private ApiClient api;
    private PlaceMapView map;
    private LinearLayout root, resultList, filterRow;
    private TextView state;
    private AlertDialog detailDialog;
    private EditText search;
    private final List<JSONObject> current = new ArrayList<>();
    private final List<JSONObject> loading = new ArrayList<>();
    private int viewportGeneration;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        if (!getPreferences(MODE_PRIVATE).getBoolean("adult_acknowledged", false)) showAgeGate();
        else showMap();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(28, 41, 45));
        t.setPadding(dp(8), dp(7), dp(8), dp(7)); return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText input(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(false); e.setHint(hint); e.setText(value == null ? "" : value);
        e.setTextSize(16); return e;
    }
    private void pad(View view) { view.setPadding(dp(12), dp(10), dp(12), dp(10)); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private String value(EditText input) { return input.getText().toString().trim(); }
    private void error(ApiClient.ApiError failure) {
        if (failure == null) return;
        if (failure.status == 401 && api.signedIn()) { api.signOut(); toast("Session expired. Sign in again to contribute."); return; }
        String message = failure.getMessage();
        JSONArray fields = failure.response == null ? null : failure.response.optJSONArray("field_errors");
        if (fields != null && fields.length() > 0) message += "\n" + fields.optJSONObject(0).optString("field", "Field") + ": " + fields.optJSONObject(0).optString("message", "Invalid value");
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show();
    }
    private static String label(String value) { return value.replace('_', ' '); }
    private static String optional(JSONObject data, String key) { return data == null || data.isNull(key) ? "" : data.optString(key, ""); }
    private String date(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) return "Not yet";
        try { return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(Instant.parse(value)); }
        catch (Exception ignored) { return value; }
    }
    private void showAgeGate() {
        root = column(); pad(root); root.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text("iYosi", 32));
        root.addView(text("A community map of smoking areas in Metro Manila", 20));
        root.addView(text("For adults only. A reported place is not permission to smoke there. Always follow posted signs, venue rules and local law.", 16));
        root.addView(text("Pilot privacy notice: You can browse without an account or location permission. Near me uses a single foreground location request only after you tap it. Searches go to the iYosi API and its configured geocoder. The map provider receives requests for areas you view; tiles are cached on this device for at least seven days. An account is needed to contribute. The API stores account and submitted content; it does not store a location trail. You can request deletion from Account.", 15));
        root.addView(button("I am an adult — continue", () -> {
            getPreferences(MODE_PRIVATE).edit().putBoolean("adult_acknowledged", true).apply(); showMap();
        }));
        root.addView(button("I am under 18 — exit", this::finish));
        ScrollView scroll = new ScrollView(this); scroll.setFitsSystemWindows(true); scroll.addView(root); setContentView(scroll);
    }

    private void showMap() {
        root = column(); root.setFitsSystemWindows(true);
        LinearLayout title = row(); title.setBackgroundColor(Color.rgb(20, 65, 68));
        TextView brand = text("iYosi · Metro Manila", 20); brand.setTextColor(Color.WHITE);
        title.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        title.addView(button("Privacy", this::privacy)); root.addView(title);
        LinearLayout searchRow = row();
        search = input("Address or landmark", ""); search.setSingleLine(true);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        searchRow.addView(button("Search", this::search)); root.addView(searchRow);
        HorizontalScrollView filters = new HorizontalScrollView(this); filterRow = row(); filters.addView(filterRow); root.addView(filters);
        updateFilters();
        FrameLayout mapFrame = new FrameLayout(this);
        map = new PlaceMapView(this); mapFrame.addView(map, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout zoom = column();
        zoom.addView(button("+", () -> map.zoomBy(1)));
        zoom.addView(button("−", () -> map.zoomBy(-1)));
        FrameLayout.LayoutParams z = new FrameLayout.LayoutParams(dp(55), -2, Gravity.TOP | Gravity.RIGHT);
        mapFrame.addView(zoom, z);
        TextView attribution = text(BuildConfig.TILE_ATTRIBUTION, 11);
        attribution.setBackgroundColor(Color.argb(225, 255, 255, 255));
        FrameLayout.LayoutParams a = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        mapFrame.addView(attribution, a);
        root.addView(mapFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = row();
        actions.addView(button("Near me", this::nearMe), new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(button("Add place", () -> ensureAuth(() -> proposal(null))), new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(button("Account", this::account), new LinearLayout.LayoutParams(0, dp(50), 1));
        root.addView(actions);
        state = text("Loading places…", 14); root.addView(state);
        state.setOnClickListener(v -> loadViewport(map.bbox(), ++viewportGeneration, null));
        ScrollView results = new ScrollView(this); resultList = column(); results.addView(resultList);
        root.addView(results, new LinearLayout.LayoutParams(-1, dp(165)));
        setContentView(root);
        map.setListener(this);
        openDeepLink();
    }

    private void openDeepLink() {
        Uri uri = getIntent() == null ? null : getIntent().getData();
        if (uri != null && "iyosi".equals(uri.getScheme()) && "places".equals(uri.getHost())
                && !uri.getPathSegments().isEmpty()) {
            String id = uri.getLastPathSegment();
            getIntent().setData(null);
            detail(id);
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (map != null) openDeepLink();
    }

    private void privacy() {
        new AlertDialog.Builder(this).setTitle("Pilot privacy and map notice")
                .setMessage("Browse without sign-in or GPS. Near me asks for a one-time foreground location and does not send it to the iYosi API; nearby search for proposed places sends only the selected area pin. Address searches go to the API and its configured provider. The map provider receives requests for areas you view; tiles are cached on this device for at least seven days. Contributions and account details are held by the API; public place data omits contributor identity. Account deletion requests go to the moderation queue. This is a pilot notice; the operator must publish final contact and retention details before public launch.\n\nPlace reports are observations, not legal permission to smoke.")
                .setPositiveButton("Close", null).show();
    }
    private void updateFilters() {
        filterRow.removeAllViews();
        filterRow.addView(button("Pin legend", () -> new AlertDialog.Builder(this).setTitle("Map pin legend")
                .setMessage("Triangle: public designated type\nDiamond: venue designated type\nCircle: community outdoor report\nSquare: unverified venue claim\n\nShape is the place category. Only green/check means evidence reviewed as verified.\nBlue: community reported\nOrange: disputed\nRed: prohibited warning\nNumbered circle: zoom in to expand places")
                .setPositiveButton("Close", null).show()));
        for (int i = 0; i < KINDS.length; i++) {
            final int index = i;
            filterRow.addView(button((kinds[i] ? "☑ " : "☐ ") + KIND_LABELS[i], () -> {
                kinds[index] = !kinds[index]; updateFilters(); applyFilters();
            }));
        }
        for (int i = 0; i < STATUSES.length; i++) {
            final int index = i;
            filterRow.addView(button((statuses[i] ? "☑ " : "☐ ") + STATUS_LABELS[i], () -> {
                statuses[index] = !statuses[index]; updateFilters();
                if (index == 3) loadViewport(map.bbox(), ++viewportGeneration, null);
                else applyFilters();
            }));
        }
    }
    @Override public void viewportChanged(String bbox) {
        loadViewport(bbox, ++viewportGeneration, null);
    }
    private void loadViewport(String bbox, int generation, String cursor) {
        if (cursor == null) { loading.clear(); state.setText("Loading places…"); }
        String path = "/v1/places?bbox=" + ApiClient.encode(bbox) + "&limit=100&include_prohibited=" + statuses[3];
        if (cursor != null) path += "&cursor=" + ApiClient.encode(cursor);
        api.get(path, (data, failure) -> {
            if (generation != viewportGeneration) return;
            if (failure != null) {
                applyFilters();
                state.setText(current.isEmpty() ? "Places unavailable. Tap here to retry."
                        : "Showing previous places; refresh failed. Tap here to retry.");
                return;
            }
            JSONArray items = ApiClient.array(data, "items");
            for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item != null) loading.add(item); }
            String next = data.optString("next_cursor", "");
            if (!next.isEmpty() && !next.equals("null")) loadViewport(bbox, generation, next);
            else { current.clear(); current.addAll(loading); applyFilters(); }
        });
    }
    private void applyFilters() {
        if (map == null || resultList == null) return;
        List<JSONObject> shown = new ArrayList<>();
        for (JSONObject place : current) {
            int k = Arrays.asList(KINDS).indexOf(place.optString("kind"));
            int s = Arrays.asList(STATUSES).indexOf(place.optString("advisory_status"));
            if (k >= 0 && s >= 0 && kinds[k] && statuses[s]) shown.add(place);
        }
        map.setPlaces(shown);
        resultList.removeAllViews();
        state.setText(shown.isEmpty() ? "No places match this view and filters." : shown.size() + " places in this view · tap a pin or list item");
        for (JSONObject place : shown) {
            String id = place.optString("id");
            String description = place.optString("name") + " · " + label(place.optString("kind"))
                    + " · " + label(place.optString("advisory_status"));
            resultList.addView(button(description, () -> detail(id)));
        }
    }
    @Override public void placeTapped(String id) { detail(id); }
    @Override public void pointChosen(double lat, double lon) {
        toast(String.format(Locale.US, "Selected area pin: %.5f, %.5f. Tap Add place to propose it.", lat, lon));
    }

    private void search() {
        String query = value(search);
        if (query.length() < 3) { toast("Enter at least three characters."); return; }
        api.get("/v1/address-suggestions?q=" + ApiClient.encode(query), (data, failure) -> {
            if (failure != null) { error(failure); return; }
            JSONArray suggestions = ApiClient.array(data, "items");
            if (suggestions.length() == 0) { toast("No matching NCR addresses or landmarks."); return; }
            String[] labels = new String[suggestions.length()];
            for (int i = 0; i < labels.length; i++) labels[i] = suggestions.optJSONObject(i).optString("label");
            new AlertDialog.Builder(this).setTitle("Move map to").setItems(labels, (dialog, index) -> {
                JSONObject point = suggestions.optJSONObject(index).optJSONObject("location");
                if (point != null) map.moveTo(point.optDouble("lat"), point.optDouble("lon"), 15);
            }).show();
        });
    }

    private void nearMe() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            String provider = fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider, null, getMainExecutor(), location -> locate(location));
            } else {
                manager.requestSingleUpdate(provider, new android.location.LocationListener() {
                    @Override public void onLocationChanged(Location location) { locate(location); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                }, Looper.getMainLooper());
            }
        } catch (Exception ignored) { toast("Location unavailable. Search or pan the map instead."); }
    }
    private void locate(Location location) {
        if (location == null) { toast("Location unavailable. Search or pan the map instead."); return; }
        map.moveTo(location.getLatitude(), location.getLongitude(), 15);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7) {
            boolean granted = false; for (int result : grantResults) granted |= result == PackageManager.PERMISSION_GRANTED;
            if (granted) nearMe(); else toast("Location denied. Search and map browsing still work.");
        }
    }

    private void ensureAuth(Runnable continuation) {
        if (api.signedIn()) { continuation.run(); return; }
        LinearLayout form = column(); pad(form);
        EditText email = input("Email", ""); email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = input("Password (at least 12 characters)", "");
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(text("Sign in to contribute. You can browse without an account.", 15));
        form.addView(email); form.addView(password);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Contributor account").setView(form)
                .setPositiveButton("Sign in", null).setNeutralButton("Register", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> auth(false, email, password, dialog, continuation));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> auth(true, email, password, dialog, continuation));
        });
        dialog.show();
    }
    private void auth(boolean register, EditText email, EditText password, AlertDialog dialog, Runnable continuation) {
        String address = value(email), secret = password.getText().toString();
        if (!address.contains("@") || secret.length() < 12) { toast("Enter a valid email and a password of at least 12 characters."); return; }
        JSONObject body = register ? ApiClient.object("email", address, "password", secret, "adult_attested", true)
                : ApiClient.object("email", address, "password", secret);
        api.post(register ? "/v1/auth/register" : "/v1/auth/token", body, false, false, (data, failure) -> {
            if (failure != null) { error(failure); return; }
            api.setToken(data.optString("access_token")); dialog.dismiss(); continuation.run();
        });
    }
    private void account() {
        ensureAuth(() -> api.get("/v1/me", (data, failure) -> {
            if (failure != null) { error(failure); return; }
            String email = data.optString("email", "Account");
            LinearLayout panel = column(); pad(panel);
            panel.addView(text(email + " · " + data.optString("role"), 17));
            panel.addView(button("My proposals", this::submissions));
            panel.addView(button("Request account deletion", this::deletionRequest));
            panel.addView(button("Sign out", () -> { api.signOut(); toast("Signed out."); }));
            new AlertDialog.Builder(this).setTitle("Account").setView(panel).setPositiveButton("Close", null).show();
        }));
    }
    private void submissions() {
        api.get("/v1/me/place-submissions", (data, failure) -> {
            if (failure != null) { error(failure); return; }
            JSONArray items = ApiClient.array(data, "items");
            LinearLayout panel = column(); pad(panel);
            if (items.length() == 0) panel.addView(text("No proposals yet.", 16));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String line = item.optString("name") + " · " + label(item.optString("state"))
                        + "\nSubmitted " + date(item.optString("created_at"));
                String reason = item.optString("decision_reason", "");
                if (!reason.isEmpty() && !reason.equals("null")) line += "\nDecision: " + reason;
                panel.addView(text(line, 15));
            }
            showScrollDialog("My proposals", panel);
        });
    }
    private void deletionRequest() {
        new AlertDialog.Builder(this).setTitle("Request account deletion")
                .setMessage("This sends a deletion request to the pilot operator's moderation queue. It is not immediate deletion.")
                .setPositiveButton("Send request", (d, w) -> api.post("/v1/me/deletion-requests", null, true, false,
                        (data, failure) -> { if (failure != null) error(failure); else toast("Deletion request received."); }))
                .setNegativeButton("Cancel", null).show();
    }
    private AlertDialog showScrollDialog(String title, LinearLayout content) {
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        return new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private void detail(String id) {
        api.get("/v1/places/" + ApiClient.encode(id), (place, failure) -> {
            if (failure != null) {
                if (failure.status == 404) toast("This place is unavailable or was removed.");
                else error(failure);
                return;
            }
            if (place.has("merged_into") && !place.isNull("merged_into")) {
                String canonical = place.optString("merged_into");
                new AlertDialog.Builder(this).setMessage("This place was merged into another record.")
                        .setPositiveButton("Open current place", (d, w) -> detail(canonical))
                        .setNegativeButton("Close", null).show(); return;
            }
            LinearLayout panel = column(); pad(panel);
            panel.addView(text(label(place.optString("kind")) + " · " + label(place.optString("advisory_status")), 18));
            if (place.optString("advisory_status").equals("prohibited"))
                panel.addView(text("Warning: smoking is reported as prohibited here. No directions are offered.", 16));
            if (place.optString("advisory_status").equals("disputed"))
                panel.addView(text("This information is disputed. Check current signs and rules.", 16));
            panel.addView(text("A report is not permission to smoke here. Follow signs and local rules.", 14));
            if (!place.isNull("address_label")) panel.addView(text(place.optString("address_label"), 16));
            JSONObject area = place.optJSONObject("location");
            if (area != null) panel.addView(text(String.format(Locale.US, "Area pin: %.5f, %.5f", area.optDouble("lat"), area.optDouble("lon")), 14));
            JSONObject accessPoint = place.optJSONObject("access_point");
            if (accessPoint != null) panel.addView(text(String.format(Locale.US, "Public entrance: %.5f, %.5f",
                    accessPoint.optDouble("lat"), accessPoint.optDouble("lon")), 14));
            if (!place.isNull("access_notes")) panel.addView(text("Access: " + place.optString("access_notes"), 15));
            JSONObject verification = place.optJSONObject("verification");
            if (verification != null) panel.addView(text("Evidence reviewed: " + date(verification.optString("reviewed_at"))
                    + "\nSource: " + verification.optString("public_source"), 15));
            panel.addView(text("Last confirmed: " + date(place.optString("last_confirmed_at"))
                    + " · " + label(place.optString("freshness")), 15));
            JSONObject rating = place.optJSONObject("rating");
            if (rating != null) panel.addView(text("Rating: " + (rating.isNull("average") ? "No stars yet" : rating.optDouble("average") + "/5")
                    + " · " + rating.optInt("count") + " reviews", 15));
            if (place.optBoolean("directions_allowed") && !place.optString("advisory_status").equals("prohibited"))
                panel.addView(button("Directions to public access point", () -> directions(place)));
            if (!place.optString("advisory_status").equals("closed")) {
                panel.addView(button("Recent confirmations", () -> confirmations(id)));
                panel.addView(button("Reviews", () -> reviews(id)));
                panel.addView(button("Confirm existence or change", () -> ensureAuth(() -> confirm(id))));
                panel.addView(button("Rate or edit my review", () -> ensureAuth(() -> review(id))));
                panel.addView(button("Suggest a correction", () -> ensureAuth(() -> proposal(place))));
                panel.addView(button("Report this place", () -> ensureAuth(() -> report("place", id))));
            }
            if (detailDialog != null) detailDialog.dismiss();
            detailDialog = showScrollDialog(place.optString("name"), panel);
        });
    }
    private void directions(JSONObject place) {
        JSONObject point = place.optJSONObject("access_point");
        if (point == null) point = place.optJSONObject("location");
        if (point == null) return;
        String destination = String.format(Locale.US, "%f,%f", point.optDouble("lat"), point.optDouble("lon"));
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        else toast("No navigation app is installed.");
    }
    private void confirmations(String id) {
        api.get("/v1/places/" + ApiClient.encode(id) + "/confirmations?limit=50", (data, failure) -> {
            if (failure != null) { error(failure); return; }
            LinearLayout panel = column(); pad(panel);
            JSONArray items = ApiClient.array(data, "items");
            if (items.length() == 0) panel.addView(text("No observations yet.", 16));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                panel.addView(text(label(item.optString("observation")) + " · observed " + date(item.optString("observed_at")), 15));
            }
            showScrollDialog("Recent confirmations", panel);
        });
    }
    private void reviews(String id) {
        api.get("/v1/places/" + ApiClient.encode(id) + "/reviews?limit=50", (data, failure) -> {
            if (failure != null) { error(failure); return; }
            LinearLayout panel = column(); pad(panel);
            JSONArray items = ApiClient.array(data, "items");
            if (items.length() == 0) panel.addView(text("No reviews yet.", 16));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i); if (item == null) continue;
                panel.addView(text(item.optInt("stars") + "/5 · " + date(item.optString("updated_at"))
                        + "\n" + optional(item, "body"), 15));
                String reviewId = item.optString("id");
                panel.addView(button("Report this review", () -> ensureAuth(() -> report("review", reviewId))));
            }
            showScrollDialog("Reviews", panel);
        });
    }

    private Spinner choice(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter); return spinner;
    }
    private void confirm(String id) {
        LinearLayout form = column(); pad(form);
        form.addView(text("What did you observe? This does not change legal or advisory status.", 15));
        String[] observations = {"Still there", "Changed", "Gone"};
        Spinner spinner = choice(observations); form.addView(spinner);
        EditText notes = input("Optional access-note correction", ""); form.addView(notes);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Confirm place").setView(form)
                .setPositiveButton("Submit", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String suggestion = value(notes);
            if (suggestion.length() > 500) { toast("Keep the correction under 500 characters."); return; }
            String observation = new String[]{"still_there", "changed", "gone"}[spinner.getSelectedItemPosition()];
            JSONObject body = ApiClient.object("observation", observation, "observed_at", ApiClient.now());
            if (!suggestion.isEmpty()) put(body, "access_note_suggestion", suggestion);
            api.post("/v1/places/" + ApiClient.encode(id) + "/confirmations", body, true, true, (data, failure) -> {
                if (failure != null) { error(failure); return; }
                dialog.dismiss(); toast("Observation received. Changes and access suggestions enter moderation."); detail(id);
            });
        }));
        dialog.show();
    }
    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Exception exception) { throw new IllegalArgumentException(exception); }
    }
    private void review(String id) {
        LinearLayout form = column(); pad(form);
        form.addView(text("Stars rate your experience; they do not verify a place.", 15));
        Spinner stars = choice(new String[]{"1 star", "2 stars", "3 stars", "4 stars", "5 stars"});
        form.addView(stars);
        EditText body = input("Optional review (up to 1000 characters)", ""); form.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("My review").setView(form)
                .setPositiveButton("Save", null).setNeutralButton("Delete my review", null)
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (value(body).length() > 1000) { toast("Keep the review under 1000 characters."); return; }
                JSONObject data = ApiClient.object("stars", stars.getSelectedItemPosition() + 1);
                if (!value(body).isEmpty()) put(data, "body", value(body));
                api.put("/v1/places/" + ApiClient.encode(id) + "/reviews/me", data, (result, failure) -> {
                    if (failure != null) { error(failure); return; }
                    dialog.dismiss(); toast("Review saved."); detail(id);
                });
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> api.delete(
                    "/v1/places/" + ApiClient.encode(id) + "/reviews/me", (result, failure) -> {
                        if (failure != null) { error(failure); return; }
                        dialog.dismiss(); toast("Review removed."); detail(id);
                    }));
        });
        dialog.show();
    }
    private void report(String targetType, String targetId) {
        LinearLayout form = column(); pad(form);
        Spinner reason = choice(new String[]{"Wrong status", "Closed", "Access notes", "Privacy", "Harassment", "Spam", "Other"});
        EditText detail = input("Optional detail for moderators", "");
        form.addView(reason); form.addView(detail);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Report " + targetType).setView(form)
                .setPositiveButton("Send report", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (value(detail).length() > 1000) { toast("Keep the detail under 1000 characters."); return; }
            JSONObject body = ApiClient.object("target_type", targetType, "target_id", targetId,
                    "reason", REPORT_REASONS[reason.getSelectedItemPosition()]);
            if (!value(detail).isEmpty()) put(body, "detail", value(detail));
            api.post("/v1/reports", body, true, true, (result, failure) -> {
                if (failure != null) { error(failure); return; }
                dialog.dismiss(); toast("Report received by the moderation queue.");
            });
        }));
        dialog.show();
    }

    private void proposal(JSONObject original) {
        boolean correction = original != null;
        LinearLayout form = column(); pad(form);
        form.addView(text(correction ? "Suggest a correction for review. It will not change the public place until approved."
                : "Propose a place for review. Long-press the map to choose a precise area pin.", 15));
        EditText name = input("Place name or landmark", correction ? original.optString("name") : ""); form.addView(name);
        Spinner kind = choice(KIND_LABELS); form.addView(kind);
        if (correction) kind.setSelection(Math.max(0, Arrays.asList(KINDS).indexOf(original.optString("kind"))));
        CheckBox inside = new CheckBox(this); inside.setText("Inside venue (for venue places)");
        inside.setChecked(correction && original.optBoolean("inside_venue")); form.addView(inside);
        JSONObject location = correction ? original.optJSONObject("location") : null;
        String latitude = String.format(Locale.US, "%.6f", location == null ? map.chosenLat() : location.optDouble("lat"));
        String longitude = String.format(Locale.US, "%.6f", location == null ? map.chosenLon() : location.optDouble("lon"));
        EditText lat = input("Area latitude", latitude), lon = input("Area longitude", longitude);
        lat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        lon.setInputType(lat.getInputType()); form.addView(lat); form.addView(lon);
        JSONObject entrance = correction ? original.optJSONObject("access_point") : null;
        EditText entranceLat = input("Public entrance latitude (optional)", entrance == null ? "" : String.valueOf(entrance.optDouble("lat")));
        EditText entranceLon = input("Public entrance longitude (optional)", entrance == null ? "" : String.valueOf(entrance.optDouble("lon")));
        entranceLat.setInputType(lat.getInputType()); entranceLon.setInputType(lat.getInputType());
        form.addView(entranceLat); form.addView(entranceLon);
        EditText address = input("Address label (optional)", correction ? optional(original, "address_label") : ""); form.addView(address);
        EditText access = input("Public access notes (optional)", correction ? optional(original, "access_notes") : ""); form.addView(access);
        EditText basis = input("Basis for this claim (10–1000 characters)", ""); form.addView(basis);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(correction ? "Correct place" : "Propose place")
                .setView(scroll).setPositiveButton("Check and submit", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = value(name), b = value(basis), a = value(access), addressText = value(address);
            if (n.length() < 2 || n.length() > 120 || b.length() < 10 || b.length() > 1000
                    || a.length() > 500 || addressText.length() > 240) {
                toast("Check name, basis, address and note lengths."); return;
            }
            double latitudeValue, longitudeValue;
            try {
                latitudeValue = Double.parseDouble(value(lat)); longitudeValue = Double.parseDouble(value(lon));
                if (!Double.isFinite(latitudeValue) || !Double.isFinite(longitudeValue)
                        || latitudeValue < -90 || latitudeValue > 90 || longitudeValue < -180 || longitudeValue > 180) throw new NumberFormatException();
            } catch (NumberFormatException exception) { toast("Enter a valid area pin."); return; }
            JSONObject body = ApiClient.object("intent", correction ? "correct" : "create", "name", n,
                    "kind", KINDS[kind.getSelectedItemPosition()], "location", ApiClient.object("lat", latitudeValue, "lon", longitudeValue),
                    "basis", b);
            if (correction) put(body, "place_id", original.optString("id"));
            if (kind.getSelectedItemPosition() == 1 || kind.getSelectedItemPosition() == 3)
                put(body, "inside_venue", inside.isChecked());
            if (!a.isEmpty()) put(body, "access_notes", a);
            if (!addressText.isEmpty()) put(body, "address_label", addressText);
            if (!value(entranceLat).isEmpty() || !value(entranceLon).isEmpty()) {
                try {
                    double elat = Double.parseDouble(value(entranceLat)), elon = Double.parseDouble(value(entranceLon));
                    if (!Double.isFinite(elat) || !Double.isFinite(elon)
                            || elat < -90 || elat > 90 || elon < -180 || elon > 180) throw new NumberFormatException();
                    put(body, "access_point", ApiClient.object("lat", elat, "lon", elon));
                } catch (NumberFormatException exception) { toast("Enter both valid public entrance coordinates."); return; }
            }
            if (correction) submitProposal(body, dialog);
            else checkDuplicates(latitudeValue, longitudeValue, body, dialog);
        }));
        dialog.show();
    }
    private void checkDuplicates(double lat, double lon, JSONObject body, AlertDialog dialog) {
        String path = String.format(Locale.US, "/v1/places/nearby?lat=%.6f&lon=%.6f&radius_m=50&limit=20", lat, lon);
        api.get(path, (data, failure) -> {
            if (failure != null) { error(failure); return; }
            JSONArray items = ApiClient.array(data, "items");
            if (items.length() == 0) { submitProposal(body, dialog); return; }
            String[] options = new String[items.length()];
            for (int i = 0; i < options.length; i++) options[i] = items.optJSONObject(i).optString("name");
            new AlertDialog.Builder(this).setTitle("Possible nearby duplicates")
                    .setMessage("Review nearby records before proposing another pin. The API may reject a duplicate within 50 m.")
                    .setItems(options, (d, which) -> detail(items.optJSONObject(which).optString("id")))
                    .setPositiveButton("Submit proposal", (d, which) -> submitProposal(body, dialog))
                    .setNegativeButton("Keep editing", null).show();
        });
    }
    private void submitProposal(JSONObject body, AlertDialog dialog) {
        api.post("/v1/place-submissions", body, true, true, (data, failure) -> {
            if (failure != null) { error(failure); return; }
            dialog.dismiss(); map.clearChosen();
            toast("Proposal received and pending admin review.");
        });
    }
}

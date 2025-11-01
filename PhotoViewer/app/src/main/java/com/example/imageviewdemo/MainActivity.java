package com.example.imageviewdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.ContentResolver;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String site_url = "https://hongmin010.pythonanywhere.com";
    private final String api_token = "485d59af4388322d9c39e952369c598785a89e77";

    TextView textView;
    RecyclerView recyclerView;
    ImageAdapter adapter;

    CloadImage taskDownload;

    ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_PhotoViewer); // 강제 테마 적용
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ImageAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        Button btnLoad = findViewById(R.id.btn_load);
        Button btnSave = findViewById(R.id.btn_save);

        btnLoad.setOnClickListener(v -> onClickDownload(v));

        // SAF 갤러리 런처
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        new PutPostFromUri().execute(uri);
                    } else {
                        Toast.makeText(this, "이미지 선택 취소", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        btnSave.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        textView.setText("앱 시작: 동기화 / 이미지 선택 & 업로드");
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_SHORT).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        String status = "";
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                URL urlAPI = new URL(urls[0]);
                conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Token " + api_token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                status = "HTTP " + code;
                if (code == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONArray arr = new JSONArray(sb.toString());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String imageUrl = obj.optString("image", "");
                        if (!imageUrl.isEmpty()) {
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = site_url + imageUrl; // 상대경로 보정
                            }
                            try {
                                URL imgUrl = new URL(imageUrl);
                                HttpURLConnection iconn = (HttpURLConnection) imgUrl.openConnection();
                                iconn.setConnectTimeout(10000);
                                iconn.setReadTimeout(10000);
                                InputStream imgStream = iconn.getInputStream();
                                Bitmap bmp = BitmapFactory.decodeStream(imgStream);
                                bitmapList.add(bmp);
                                imgStream.close();
                                iconn.disconnect();
                            } catch (Exception ignore) {
                                Log.e("PV","img load err "+ignore.getMessage());
                            }
                        }
                    }
                    if (bitmapList.isEmpty()) status += " (목록 0개)";
                } else {
                    InputStream es = conn.getErrorStream();
                    if (es != null) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(es));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        status += " " + sb.toString();
                    }
                }
            } catch (Exception e) {
                status = "ERROR: " + e.getMessage();
                Log.e("PV","GET err", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            textView.setText("동기화 결과: " + status + " / 이미지 " + (images==null?0:images.size())+"개");
            if (images != null && !images.isEmpty()) {
                adapter = new ImageAdapter(images);
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private class PutPostFromUri extends AsyncTask<Uri, Void, String> {
        @Override
        protected String doInBackground(Uri... uris) {
            if (uris == null || uris.length == 0 || uris[0] == null) return "ERROR: no uri";
            Uri uri = uris[0];

            String apiUrl = site_url + "/api_root/Post/";
            String boundary = "----AndroidBoundary" + UUID.randomUUID().toString().replace("-", "");
            String LINE = "\r\n";

            HttpURLConnection conn = null;
            try {
                // 파일명, MIME
                ContentResolver cr = getContentResolver();
                String mime = cr.getType(uri);
                if (mime == null) mime = "image/jpeg";

                String filename = "upload.jpg";
                Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) filename = c.getString(idx);
                    }
                    c.close();
                }

                // 파일 바이트
                InputStream is = cr.openInputStream(uri);
                byte[] imageBytes = readAllBytes(is);
                if (is != null) is.close();

                // 연결
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", "Token " + api_token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());

                // title
                out.writeBytes("--" + boundary + LINE);
                out.writeBytes("Content-Disposition: form-data; name=\"title\"" + LINE + LINE);
                out.writeBytes("Android Upload" + LINE);

                // text
                out.writeBytes("--" + boundary + LINE);
                out.writeBytes("Content-Disposition: form-data; name=\"text\"" + LINE + LINE);
                out.writeBytes("Uploaded from gallery" + LINE);

                // image
                out.writeBytes("--" + boundary + LINE);
                out.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + filename + "\"" + LINE);
                out.writeBytes("Content-Type: " + mime + LINE + LINE);
                out.write(imageBytes);
                out.writeBytes(LINE);

                out.writeBytes("--" + boundary + "--" + LINE);
                out.flush();
                out.close();

                int responseCode = conn.getResponseCode();
                InputStream resp = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(resp));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                return "HTTP " + responseCode + " " + sb.toString();

            } catch (Exception e) {
                Log.e("PV","POST err", e);
                return "ERROR: " + e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            textView.setText("Upload result: " + s);
            if (s.startsWith("HTTP 200") || s.startsWith("HTTP 201")) {
                new CloadImage().execute(site_url + "/api_root/Post/");
            }
        }
    }

    // util
    private byte[] readAllBytes(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}

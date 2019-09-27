package com.vapstor.codenation;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import com.vapstor.codenation.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.vapstor.codenation.utils.Utils.getSha1;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {

    private final String URL_API_GET = "https://api.codenation.dev/v1/challenge/dev-ps/generate-data?token=7f9395e2b58d83ba2c795568db88f3dba6c376f1";
    private final String URL_API_POST = "https://api.codenation.dev/v1/challenge/dev-ps/submit-solution?token=7f9395e2b58d83ba2c795568db88f3dba6c376f1";
    private DigestAuthenticator authenticator;
    private OkHttpClient client;
    private MediaType FORM = MediaType.parse("multipart/form-data");
    private MediaType JSON = MediaType.parse("application/json");
    private Map<String, CachingAuthenticator> authCache;
    public static ProgressDialog progressDialog;
    private TextView status, numeroCasas, token, cifrado, decifrado, resumoCriptografico;
    private Button button, buttonFinaliza;
    private JSONObject challenge;
    private String txtDecifrado, txtResumoCriptografico;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Conectando");
        progressDialog.setMessage("Aguarde um momento");
        progressDialog.setCancelable(true);

        status = findViewById(R.id.status);
        button = findViewById(R.id.button);
    }

    public void executeGET(View v) {
        final String[] jsonString = new String[1];
        new Thread(() -> {
            if(!progressDialog.isShowing()) {
                runOnUiThread(() -> progressDialog.show());
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                jsonString[0] = new AsyncTask<Void, Void, String>(){
                    @Override
                    protected void onPostExecute(String aVoid) {
                        if(progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        super.onPostExecute(aVoid);
                    }

                    @Override
                    protected String doInBackground(Void... voids) {
                        conectaAPI();
                        try {
                            return run(URL_API_GET);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                }.execute().get();
                runOnUiThread(() -> {
                    Toast.makeText(this, "GET EFETUADO!", Toast.LENGTH_SHORT).show();
                    status.setText("Desafio Recuperado!");
                });

                if(saveJSON(jsonString[0])) {
                    runOnUiThread(() -> {
                        this.setContentView(R.layout.second_activity);
                        buttonFinaliza = findViewById(R.id.button2);

                        numeroCasas = findViewById(R.id.numero_casas);
                        token = findViewById(R.id.token);
                        cifrado = findViewById(R.id.cifrado);
                        decifrado = findViewById(R.id.decifrado);
                        resumoCriptografico = findViewById(R.id.resumo_criptografico);
                        try {
                            challenge = new JSONObject(jsonString[0]);
                            numeroCasas.setText("NUMERO DE CASAS: "+ challenge.getInt("numero_casas"));
                            token.setText("TOKEN: "+challenge.getString("token"));
                            cifrado.setText("CIFRADO: "+challenge.getString("cifrado"));
//                            decifrado.setText("DECIFRADO: "+challenge.getString("decifrado"));
//                            resumoCriptografico.setText("RESUMO: "+challenge.getString("resumo_criptografico"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });
                }

            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void resolveChallenge(View view) {
        txtDecifrado = descriptografa(cifrado.getText().toString().replace("CIFRADO: ", ""));
        decifrado.setText("DECIFRADO: " + txtDecifrado);
        txtResumoCriptografico = getSha1(txtDecifrado);
        resumoCriptografico.setText("RESUMO: " + txtResumoCriptografico);
        buttonFinaliza.setEnabled(true);
    }

    public void navigatePostScreen(View v) throws JSONException {
        setContentView(R.layout.third_activity);
        challenge.put("decifrado", txtDecifrado);
        challenge.put("resumo_criptografico", txtResumoCriptografico);
        if(refreshJSON(challenge)) {
            TextView status = findViewById(R.id.status);
            status.setText("Arquivo atualizado e pronto para enviar!");
            Toast.makeText(this, "JSON Atualizado!", Toast.LENGTH_SHORT).show();
        }
    }

    public void postChallenge(View v) {
        boolean isFilePresent = isFilePresent("answer");
        if(isFilePresent) {
            new Thread(() -> {
                switch(uploadFile(read())){
                    case 200:
                        runOnUiThread(() -> Toast.makeText(this, "JSON POSTADO COM SUCESSO! (200)", Toast.LENGTH_LONG).show());
                        break;
                    case 400:
                        runOnUiThread(() -> Toast.makeText(this, "BAD RESQUEST! (400)", Toast.LENGTH_LONG).show());
                        break;
                    case 429:
                        runOnUiThread(() -> Toast.makeText(this, "TOO MANY RESQUESTS! (429)", Toast.LENGTH_LONG).show());
                        break;
                    case 0:
                    default:
                        runOnUiThread(() -> Toast.makeText(this, "UNKNOW ERROR! (0)", Toast.LENGTH_LONG).show());
                        break;
                }
                finish();
                startActivity(getIntent());
            }).start();
        } else {
            Toast.makeText(this, "JSON NÃO ENCONTRADO!", Toast.LENGTH_SHORT).show();
            finish();
            startActivity(getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }


    private String descriptografa(String frase) {
        char[] charArray = frase.toCharArray();
        char[] returnArray = new char[charArray.length];
        char myChar;
        for (int i = 0; i < charArray.length; i++) {
            switch (charArray[i]) {
                case ' ':
                    myChar = ' ';
                    break;
                case 'v':
                    myChar = 'z';
                    break;
                case 'w':
                    myChar = 'a';
                    break;
                case 'x':
                    myChar = 'b';
                    break;
                case 'y':
                    myChar = 'c';
                    break;
                case 'z':
                    myChar = 'd';
                    break;
                case 'a':
                    myChar = 'e';
                    break;
                case 'b':
                    myChar = 'f';
                    break;
                case 'c':
                    myChar = 'g';
                    break;
                case 'd':
                    myChar = 'h';
                    break;
                case 'e':
                    myChar = 'i';
                    break;
                case 'f':
                    myChar = 'j';
                    break;
                case 'g':
                    myChar = 'k';
                    break;
                case 'h':
                    myChar = 'l';
                    break;
                case 'i':
                    myChar = 'm';
                    break;
                case 'j':
                    myChar = 'n';
                    break;
                case 'k':
                    myChar = 'o';
                    break;
                case 'l':
                    myChar = 'p';
                    break;
                case 'm':
                    myChar = 'q';
                    break;
                case 'n':
                    myChar = 'r';
                    break;
                case 'o':
                    myChar = 's';
                    break;
                case 'p':
                    myChar = 't';
                    break;
                case 'q':
                    myChar = 'u';
                    break;
                case 'r':
                    myChar = 'v';
                    break;
                case 's':
                    myChar = 'w';
                    break;
                case 't':
                    myChar = 'x';
                    break;
                case 'u':
                    myChar = 'y';
                    break;
                default:
                    myChar = '?';
                    break;
            }
            returnArray[i] = myChar;
        }
        return new String(returnArray);
    }

    private boolean saveJSON(String jsonString) {
        try{
            BufferedWriter file = new BufferedWriter(new FileWriter(getFilesDir() +"/answer.json"));
            file.write(jsonString);
            return true;
        } catch (FileNotFoundException fileNotFound) {
            return false;
        } catch (IOException ioException) {
            return false;
        }
    }

    private boolean refreshJSON(JSONObject challenge){
        //Copiar dados para JSON
        try (BufferedWriter file = new BufferedWriter(new FileWriter(getFilesDir() +"/answer.json"))) {
            file.write(challenge.toString());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private File read() {
        return new File(getFilesDir()+"/answer.json");
////        File ret = null;
////        try {
////            InputStream inputStream = new FileInputStream(new File(getFilesDir()+"answer"));
////            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
////            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
////            String receiveString = "";
////            StringBuilder stringBuilder = new StringBuilder();
////            while ( (receiveString = bufferedReader.readLine()) != null ) {
////                stringBuilder.append(receiveString);
////            }
////            inputStream.close();
////            ret = new File(stringBuilder.toString());
//        }
//        catch (FileNotFoundException e) {
//            Log.e("FileToJson", "File not found: " + e.toString());
//        } catch (IOException e) {
//            Log.e("FileToJson", "Can not read file: " + e.toString());
//        }
//        return ret;
    }

    public boolean isFilePresent(String fileName) {
        String path = getFilesDir() + fileName;
        File file = new File(path);
        return file.exists();
    }

    private void conectaAPI() {
        authenticator = new DigestAuthenticator(new Credentials("", ""));
        //authenticator = new DigestAuthenticator(new Credentials("admin", "1234"));
        authCache = new ConcurrentHashMap<>();
        client = Utils.getUnsafeOkHttpClient()
            .authenticator(new CachingAuthenticatorDecorator(authenticator, authCache))
            .addInterceptor(new AuthenticationCacheInterceptor(authCache))
            .build();
    }


    private int uploadFile(File file) {
        try {
            String name = file.getName();
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("answer", name,
                        RequestBody.create(file, MediaType.parse("multipart/form-data")))
//                .addFormDataPart("some-field", "some-value")
                .build();

            Request request = new Request.Builder()
                .url(URL_API_POST)
                .post(requestBody)
                .build();

            Response response = client.newCall(request).execute();
            return response.code();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return 0;
    }

    public String run(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .build();
        Response response = client.newCall(request).execute();
        return Objects.requireNonNull(response.body()).string();
    }



}

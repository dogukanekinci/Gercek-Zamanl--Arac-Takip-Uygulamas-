package com.example.gercekzamanliaractakipbinici;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;


import com.bumptech.glide.Glide;
import com.example.gercekzamanliaractakipbinici.Common.Common;
import com.example.gercekzamanliaractakipbinici.Utils.UserUtils;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gercekzamanliaractakipbinici.databinding.ActivityHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST =1000 ; //herhangi bir numara
    private AppBarConfiguration mAppBarConfiguration;
    private DrawerLayout drawer;
    private NavigationView navigationView;
    private NavController navController;
    private ImageView img_avatar;
    private Uri imageUri;
    private AlertDialog waitingDialog;
    private StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = findViewById(R.id.toolbar); //toolbar import//
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
         navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
         navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_home);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);


        init();

    }
    private void init(){
        storageReference = FirebaseStorage.getInstance().getReference();

        waitingDialog= new AlertDialog.Builder(this)
                .setCancelable(false)
                .setMessage("Bekleyiniz...")
                .create();

        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_sign_out)
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
                builder.setTitle("Çıkış yap")
                        .setMessage("Çıkış yapmak istiyor musunuz?")
                        .setNegativeButton("İPTAL", (dialogInterface, i) -> dialogInterface.dismiss())
                        .setPositiveButton("Çıkış Yap", (dialogInterface, i) -> {
                            FirebaseAuth.getInstance().signOut();
                            Intent intent = new Intent(HomeActivity.this,SplashScreenActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false);
                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(dialogInterface ->{
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setTextColor(ContextCompat.getColor(HomeActivity.this,android.R.color.holo_red_dark));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setTextColor(ContextCompat.getColor(HomeActivity.this,R.color.colorAccent));
                });
                dialog.show();
            }
            return true;
        });
        //set data for user
        View headerView= navigationView.getHeaderView(0);
        TextView txt_name = (TextView) headerView.findViewById(R.id.txt_name);
        TextView txt_phone = (TextView) headerView.findViewById(R.id.txt_phone);

        img_avatar = (ImageView)headerView.findViewById(R.id.img_avatar);

        txt_name.setText(Common.buildWelcomeMessage());
        txt_phone.setText(Common.currentRider != null ? Common.currentRider.getPhoneNumber() :"");

        img_avatar.setOnClickListener(view -> {
            Intent intent= new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent,PICK_IMAGE_REQUEST);
        });

        if(Common.currentRider !=null && Common.currentRider.getAvatar() != null &&
                !TextUtils.isEmpty(Common.currentRider.getAvatar()))
        {
            Glide.with(this)
                    .load(Common.currentRider.getAvatar())
                    .into(img_avatar);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_home);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK)
        {
            if(data != null && data.getData() != null)
            {
                imageUri = data.getData();
                img_avatar.setImageURI(imageUri);

                showDialogUpload();
            }
        }

    }

    private void showDialogUpload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
        builder.setTitle("Avatarı Değiştir")
                .setMessage("Avatarınızı değiştirmek istiyor musunuz?")
                .setNegativeButton("İPTAL", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("YÜKLE", (dialogInterface, i) -> {
                    if (imageUri != null)
                    {
                        waitingDialog.setMessage("Yükleniyor...");
                        waitingDialog.show();

                        String unique_name = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        StorageReference avatarFolder = storageReference.child("avatars/"+unique_name);
                        avatarFolder.putFile(imageUri)
                                .addOnFailureListener(e -> {
                                    waitingDialog.dismiss();
                                    Snackbar.make(drawer,e.getMessage(),Snackbar.LENGTH_SHORT).show();
                                }).addOnCompleteListener(task -> {
                            if (task.isSuccessful())
                            {
                                avatarFolder.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        Map<String,Object> updateData = new HashMap<>();
                                        updateData.put("avatar",uri.toString());
                                        UserUtils.updateUser(drawer,updateData);
                                    }
                                });
                                waitingDialog.dismiss();
                            }
                        }).addOnProgressListener(taskSnapshot -> {
                            double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                            waitingDialog.setMessage(new StringBuilder("Yükleniyor: ").append(progress).append("%"));
                        });
                    }
                })
                .setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface ->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getResources().getColor(R.color.colorAccent));
        });
        dialog.show();
    }
}
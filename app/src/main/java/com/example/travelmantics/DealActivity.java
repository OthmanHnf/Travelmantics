package com.example.travelmantics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.firebase.ui.auth.data.model.Resource;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

public class DealActivity extends AppCompatActivity {

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mDatabaseReference;
    private static int PICTURE_RESULT = 42;
    EditText textTitle;
    EditText textDescription;
    EditText textPrice;
    ImageView imageView;
    TravelDeal deal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deal);

        mFirebaseDatabase = FirebaseUtil.mFirebaseDatabase;
        mDatabaseReference = FirebaseUtil.mDatabaseReference;

        textTitle = (EditText) findViewById(R.id.text_title);
        textDescription = findViewById(R.id.text_description);
        textPrice = findViewById(R.id.text_price);
        imageView = findViewById(R.id.image_deal);

        Intent intent = getIntent();
        TravelDeal deal = (TravelDeal) intent.getSerializableExtra("Deal");
        if (deal==null)
            deal = new TravelDeal();
        this.deal = deal;
        textTitle.setText(deal.getTitle());
        textDescription.setText(deal.getDescription());
        textPrice.setText(deal.getPrice());
        showImage(deal.getImageUrl());

        Button buttonImage = findViewById(R.id.button_upload_image);
        buttonImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(intent.createChooser(intent,
                        "Insert Picture"), PICTURE_RESULT);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        Write to database on save menu click
        switch (item.getItemId()){
            case R.id.menu_deal_save:
                saveDeal();
                Toast.makeText(this, "Deal saved", Toast.LENGTH_LONG).show();
                clean();
                backToList();
                return true;
            case R.id.menu_deal_delete:
                deleteDeal();
                backToList();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        return super.onCreateOptionsMenu(menu);
//        Add menu to the activity
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_deal, menu);

        if (FirebaseUtil.isAdmin == true){
            menu.findItem(R.id.menu_deal_save).setVisible(true);
            menu.findItem(R.id.menu_deal_delete).setVisible(true);
            enableEditTexts(true);
            findViewById(R.id.button_upload_image).setVisibility(imageView.VISIBLE);
        }
        else{
            menu.findItem(R.id.menu_deal_save).setVisible(false);
            menu.findItem(R.id.menu_deal_delete).setVisible(false);
            enableEditTexts(false);
            findViewById(R.id.button_upload_image).setVisibility(imageView.INVISIBLE);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PICTURE_RESULT && resultCode == RESULT_OK){
            // Upload image
            Uri imageUri = data.getData();
            final StorageReference ref =
                    FirebaseUtil.mStorageReference.child(imageUri.getLastPathSegment());
            UploadTask uploadTask = ref.putFile(imageUri);

            // taskSnapshot.getDownloadUrl() Depreciated
//            uploadTask.addOnSuccessListener(this,
//                    new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                @Override
//                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                    String url = taskSnapshot.getDownloadUrl().toString();
//                }
//            });

            // Get uploaded image's URL
            Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    // Continue with the task to get the download URL
                    return ref.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();

                        String url = downloadUri.toString();
                        deal.setImageUrl(url);

                        String pictureName = downloadUri.getLastPathSegment();
                        deal.setImageName(pictureName);
                        Log.d("Url", "onComplete: " + url);
                        Log.d("Name", "onComplete: " + pictureName);
                        Log.d("ref Name", "onComplete: " + ref.getName());

                        showImage(url);
                    } else {
                        // Handle failures
                        Toast.makeText(DealActivity.this, "Upload failed", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void saveDeal() {
        deal.setTitle(textTitle.getText().toString());
        deal.setDescription(textDescription.getText().toString());
        deal.setPrice(textPrice.getText().toString());
        if (deal.getId()==null)
            mDatabaseReference.push().setValue(deal);
        else
            mDatabaseReference.child(deal.getId()).setValue(deal);
    }

    private void deleteDeal(){
        if (deal.getId()==null) {
            Toast.makeText(this, "Please save deal before deleting", Toast.LENGTH_SHORT).show();
            return;
        }
        mDatabaseReference.child(deal.getId()).removeValue();
        if(deal.getImageName() != null && deal.getImageName().isEmpty() == false){
            StorageReference picRef = FirebaseUtil.mFirebaseStorage.getReference().
                    child(deal.getImageName());
            picRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Log.d("Delete image", "onSuccess: Image successfully deleted");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("Delete image", "onFailure: " + e.getMessage());
                }
            });
        }
        Toast.makeText(this, "Deal deleted", Toast.LENGTH_LONG).show();
    }

    private void backToList(){
        Intent intent = new Intent(this, ListActivity.class);
        startActivity(intent);
    }

    private void clean() {
        textTitle.setText("");
        textDescription.setText("");
        textPrice.setText("");
        textTitle.requestFocus();
    }

    private void enableEditTexts(boolean isEnabled){
        textTitle.setEnabled(isEnabled);
        textDescription.setEnabled(isEnabled);
        textPrice.setEnabled(isEnabled);

    }

    private void showImage(String url){
        if (url != null && url.isEmpty() == false) {
            int width = Resources.getSystem().getDisplayMetrics().widthPixels;
            Picasso.get().load(url).resize(width, width*2/3).centerCrop().into(imageView);
        }
    }
}

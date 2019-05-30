package com.example.speechoscope;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    EditText name;
    EditText age;
    EditText email;
    EditText pass;
    Button b;
    FirebaseFirestore db=FirebaseFirestore.getInstance();
    CollectionReference uss=db.collection("users");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        b = findViewById(R.id.button);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                name=(EditText)findViewById(R.id.name);
                age=(EditText)findViewById(R.id.age);
                email=(EditText)findViewById(R.id.email);
                pass=(EditText)findViewById(R.id.pass);
                final Map<String, Object> user = new HashMap<>();
                user.put("email", email.getText().toString());
                CollectionReference reference = db.collection("users");
                reference.whereEqualTo("email",email.getText().toString()).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        boolean exists=false;
                        if(task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                if (document.exists()) {
                                    Toast.makeText(MainActivity.this, "User Already Exists", Toast.LENGTH_SHORT).show();
                                    exists = true;
                                }
                            }
                            if (!exists) {
                                //ShareIntent
                                Toast.makeText(MainActivity.this, "New User", Toast.LENGTH_SHORT).show();
                                Intent intent=new Intent(MainActivity.this,RecordSample.class);
                                intent.putExtra("name",name.getText().toString());
                                intent.putExtra("age",age.getText().toString());
                                intent.putExtra("email",email.getText().toString());
                                intent.putExtra("password",pass.getText().toString());
                                startActivity(intent);
                            }
                        }
                        else
                        {
                            Toast.makeText(MainActivity.this, "FAILLLLLLLLEDDDDDD AGAIN", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }
        });
    }

}

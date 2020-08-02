package com.example.encryptchat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.firebase.ui.database.FirebaseListOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

public class MainActivity extends AppCompatActivity {

    private static final int SIGN_IN_REQUEST_CODE = 1000;
    private static int KEY = 12;
    private FirebaseListAdapter<ChatMessage> adapter;
    private boolean forceRefresh = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // if an user is not already signed into our app, then we display sign in page using this
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Start sign in/sign up activity
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .build(),
                    SIGN_IN_REQUEST_CODE
            );
        } else {
            // User is already signed in. Therefore, display
            // a welcome Toast
            Toast.makeText(this,
                    "Welcome " + FirebaseAuth.getInstance()
                            .getCurrentUser()
                            .getDisplayName(),
                    Toast.LENGTH_LONG)
                    .show();

            // Load chat room contents
            displayChatMessages();
        }

        // Button is used to add a chat message
        ImageView fab = findViewById(R.id.fab);

        // fab in this case is the send button
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // This is the place where we enter message text
                EditText input = findViewById(R.id.input);

                // Read the input field and push a new instance/object
                // of ChatMessage project to the Firebase database
                // EncryptText method in the below code is responsible of encrypting the message
                try {
                    FirebaseDatabase.getInstance()
                            .getReference()
                            .push()
                            .setValue(new ChatMessage(encryptText(input.getText().toString()),
                                    FirebaseAuth.getInstance()
                                            .getCurrentUser()
                                            .getDisplayName())
                            )
                            .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Show error message if we were unable to push that message to database
                            Toast.makeText(MainActivity.this, "A technical error occured", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    // Show error message if we were unable to push that message to database
                    Toast.makeText(MainActivity.this, "A technical error occured", Toast.LENGTH_LONG).show();
                }

                // Clear the input
                input.setText("");
            }
        });
    }

    // This method/function is used to display chat messages
    private void displayChatMessages() {
        ListView listOfMessages = (ListView) findViewById(R.id.list_of_messages);

        // The below code was taken from firebase docs, it is used to connect database with our app
        Query query = FirebaseDatabase.getInstance().getReference();

        FirebaseListOptions<ChatMessage> options = new FirebaseListOptions.Builder<ChatMessage>()
                .setLayout(R.layout.message)
                .setQuery(query, ChatMessage.class)
                .build();

        // This populates the view on our app based on the messages received from database
        adapter = new FirebaseListAdapter<ChatMessage>(options) {
            @Override
            protected void populateView(View v, ChatMessage model, int position) {
                // Get references to the views of message.xml
                TextView messageText = (TextView) v.findViewById(R.id.message_text);
                TextView messageUser = (TextView) v.findViewById(R.id.message_user_name);
                TextView messageTime = (TextView) v.findViewById(R.id.message_time);

                // Set their text
                try {
                    messageText.setText(decryptText(model.getMessageText()));
                } catch (Exception e) {
                    messageText.setText("Error displaying this message");
                }
                messageUser.setText(model.getMessageUserName());

                // Format the date from epoch before showing it
                messageTime.setText(DateFormat.format("dd-MM-yyyy (HH:mm:ss)",
                        model.getMessageTime()));
            }
        };

        listOfMessages.setAdapter(adapter);

        // We can forcefully refresh messages by using this forceRefresh boolean
        if(forceRefresh) {
            adapter.onDataChanged();
            forceRefresh = false;
        }
    }

    // This method is called after user logs in
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SIGN_IN_REQUEST_CODE) {
            // This is log in successful use case
            if (resultCode == RESULT_OK) {
                Toast.makeText(this,
                        "Successfully signed in. Welcome!",
                        Toast.LENGTH_LONG)
                        .show();
                forceRefresh = true;
                displayChatMessages();

                // This is log in unsuccessful use case
            } else {
                Toast.makeText(this,
                        "We couldn't sign you in. Please try again later.",
                        Toast.LENGTH_LONG)
                        .show();

                // Close the app
                finish();
            }
        }
    }


    // This is used to display sign out button on the top right corner. The top portion is called app bar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }


    // This method is used to sign out user
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_sign_out) {
            AuthUI.getInstance().signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(MainActivity.this,
                                    "You have been signed out.",
                                    Toast.LENGTH_LONG)
                                    .show();

                            // Close activity
                            finish();
                        }
                    });
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
        }
    }

    private String encryptText(String text) throws Exception {
        int offset = KEY % 26 + 26;
        StringBuilder encoded = new StringBuilder();
        for (char i : text.toCharArray()) {
            if (Character.isLetter(i)) {
                if (Character.isUpperCase(i)) {
                    encoded.append((char) ('A' + (i - 'A' + offset) % 26));
                } else {
                    encoded.append((char) ('a' + (i - 'a' + offset) % 26));
                }
            } else {
                encoded.append(i);
            }
        }
        return encoded.toString();
    }

    private String decryptText(String text) throws Exception {
        int offset = (26 - KEY) % 26 + 26;
        StringBuilder decoded = new StringBuilder();
        for (char i : text.toCharArray()) {
            if (Character.isLetter(i)) {
                if (Character.isUpperCase(i)) {
                    decoded.append((char) ('A' + (i - 'A' + offset) % 26));
                } else {
                    decoded.append((char) ('a' + (i - 'a' + offset) % 26));
                }
            } else {
                decoded.append(i);
            }
        }
        return decoded.toString();
    }
}

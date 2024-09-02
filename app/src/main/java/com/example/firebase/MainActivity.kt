@file:Suppress("DEPRECATION")

package com.example.firebase

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference
    private lateinit var getContent: ActivityResultLauncher<String>
    private var imageUri: Uri? = null

    private val REQUEST_CODE = 1000 // For runtime permission

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        storageRef = storage.reference

        // Setup ActivityResultLauncher for image selection
        getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            imageUri = uri
        }

        // Find views
        val usernameEditText = findViewById<EditText>(R.id.username)
        val emailEditText = findViewById<EditText>(R.id.email)
        val passwordEditText = findViewById<EditText>(R.id.password)
        val dobEditText = findViewById<EditText>(R.id.dob)
        val genderSpinner = findViewById<Spinner>(R.id.gender)
        val uploadImageButton = findViewById<Button>(R.id.upload_image)
        val registerButton = findViewById<Button>(R.id.register_button)

        // Request runtime permissions if necessary
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE
            )
        }

        // Setup button listeners
        uploadImageButton.setOnClickListener {
            getContent.launch("image/*")
        }



        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val username = usernameEditText.text.toString().trim()
            val dob = dobEditText.text.toString().trim()
            val gender = genderSpinner.selectedItem.toString().trim()


            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // Check if email is already in use
            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { fetchTask ->
                if (fetchTask.isSuccessful) {
                    val signInMethods = fetchTask.result.signInMethods
                    if (!signInMethods.isNullOrEmpty()) {
                        // Email already in use
                        Toast.makeText(
                            this,
                            "Email already registered. Please use a different email or sign in.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Email not in use, proceed with registration
                        registerUser(email, password, username, dob, gender)
                    }
                } else {
                    val exception = fetchTask.exception
                    val errorMessage =
                        exception?.localizedMessage ?: "Failed to check email availability."
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun registerUser(
        email: String,
        password: String,
        username: String,
        dob: String,
        gender: String
    ) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.let {
                    saveUserData(it.uid, username, email, dob, gender)
                }
            } else {
                val exception = task.exception
                val errorMessage = exception?.localizedMessage ?: "Authentication failed."
                Toast.makeText(baseContext, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserData(
        userId: String,
        username: String,
        email: String,
        dob: String,
        gender: String
    ) {
        val user = hashMapOf(
            "username" to username,
            "email" to email,
            "dob" to dob,
            "gender" to gender
        )

        db.collection("users").document(userId).set(user)
            .addOnSuccessListener {
                Toast.makeText(baseContext, "User data saved.", Toast.LENGTH_SHORT).show()
                if (imageUri != null) {
                    uploadImage(userId)
                }
            }
            .addOnFailureListener { exception ->
                val errorMessage = exception.localizedMessage ?: "Error saving user data."
                Toast.makeText(baseContext, errorMessage, Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImage(userId: String) {
        val fileRef = storageRef.child("images/$userId.jpg")
        imageUri?.let { uri ->
            val uploadTask = fileRef.putFile(uri)

            uploadTask.addOnSuccessListener {
                Log.d("Upload", "Image uploaded successfully")
                Toast.makeText(baseContext, "Image uploaded.", Toast.LENGTH_SHORT).show()
            }

            uploadTask.addOnFailureListener { exception ->
                Log.e("UploadImage", "Error uploading image", exception)
                Toast.makeText(baseContext, "Image upload failed.", Toast.LENGTH_SHORT).show()
            }



            fun onRequestPermissionsResult(
                requestCode: Int,
                permissions: Array<out String>,
                grantResults: IntArray
            ) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                if (requestCode == REQUEST_CODE) {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // Permission granted, you can perform your action here
                    } else {
                        // Permission denied, handle accordingly
                        Toast.makeText(
                            this,
                            "Permission denied. Cannot access storage.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}

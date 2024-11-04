package com.puj.localizer


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {

    private lateinit var nameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    val database = FirebaseDatabase.getInstance()
    val userRef = database.getReference("usuarios")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Referencias a los elementos de la UI
        nameEditText = findViewById(R.id.nameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()

            if (name.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            } else {
                auth = Firebase.auth
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) {
                                saveUserDataToFirestore(user.uid, name, email)
                                saveToDb(name, phone, user.uid)
                                updateUI(user)
                            }
                        } else {
                            val errorMessage = task.exception?.message
                            Toast.makeText(baseContext, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun saveUserDataToFirestore(userId: String, name: String, email: String) {
        // Guardar los datos del usuario en Firestore
        val user = hashMapOf(
            "name" to name,
            "email" to email
        )

        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // Redirigir a la pantalla principal
            startActivity(Intent(this, LogInActivity::class.java))
            finish()
        }
    }

    private fun saveToDb(nombre: String, tel: String, id: String){
        val data = mapOf(
            "Nombre" to nombre,
            "Telefono" to tel
        )
        val initialCoords = mapOf(
            "Lat" to 4.7110,
            "Long" to -74.0721
        )

        //Datos del usuario
        userRef.child(id).setValue(data)
            .addOnFailureListener{ error ->
            Log.e("Firebase", "Error agregando los datos del usuario", error)
        }
        //Posicion inicial (Bogota)
        userRef.child(id).child("Posicion").setValue(initialCoords)
            .addOnFailureListener{ error ->
            Log.e("Firebase", "Error agregando las coordenadas del usuario", error)
        }
    }
}
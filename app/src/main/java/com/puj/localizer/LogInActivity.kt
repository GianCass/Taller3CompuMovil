package com.puj.localizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.puj.localizer.databinding.ActivityLoginBinding
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LogInActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        auth = Firebase.auth

        val btnEnter: Button = findViewById(R.id.btnLogin)
        btnEnter.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // El usuario ha iniciado sesión correctamente
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user != null) {
                                Toast.makeText(this, "Bienvenido, ${user.email}", Toast.LENGTH_SHORT).show()
                                // Redirigir a la pantalla principal
                                val intent = Intent(this, MapActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            // Si el inicio de sesión falla, muestra un mensaje
                            Toast.makeText(this, "Error al iniciar sesión: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor, introduce el correo y la contraseña", Toast.LENGTH_SHORT).show()
            }
        }

        val btnRegister: Button = findViewById(R.id.btnRegister)
        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
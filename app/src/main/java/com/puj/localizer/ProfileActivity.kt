package com.puj.localizer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ValueEventListener
import com.puj.localizer.databinding.ActivityPerfilBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var nameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var changePhotoButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private var imageUri: Uri? = null

    companion object {
        const val REQUEST_IMAGE_GALLERY = 100
        const val REQUEST_IMAGE_CAMERA = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        // Inicializar Firebase Auth, Database y Storage
        auth = Firebase.auth
        database = Firebase.database.reference
        storage = Firebase.storage

        // Asignar vistas
        profileImageView = findViewById(R.id.profileImageView)
        nameEditText = findViewById(R.id.nameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        saveButton = findViewById(R.id.saveButton)
        changePhotoButton = findViewById(R.id.changePhotoButton)

        loadUserData() // Cargar datos del usuario

        saveButton.setOnClickListener {
            val newName = nameEditText.text.toString().trim()
            val newPhone = phoneEditText.text.toString().trim()
            val newPassword = passwordEditText.text.toString().trim()
            updateUserData(newName, newPhone, newPassword)
        }

        changePhotoButton.setOnClickListener {
            openImagePicker()
        }
    }

    // Cargar la información del usuario desde la base de datos
    private fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Recuperar información básica del usuario
            database.child("usuarios").child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    nameEditText.setText(snapshot.child("Nombre").getValue(String::class.java))
                    phoneEditText.setText(snapshot.child("Telefono").getValue(String::class.java))
                    loadProfileImage(userId)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileActivity, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // Cargar la foto de perfil del usuario desde Firebase Storage
    private fun loadProfileImage(userId: String) {
        val profileImageRef = storage.reference.child("profile_images/$userId.jpg")
        profileImageRef.downloadUrl.addOnSuccessListener { uri ->
            // Cargar la imagen en el ImageView (usa Glide, Picasso, etc.)
            profileImageView.setImageURI(uri)
        }.addOnFailureListener {
            // Maneja errores de carga de imagen
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // Actualizar información del usuario en Firebase
    private fun updateUserData(name: String, phone: String, password: String) {
        val userId = auth.currentUser?.uid ?: return

        // Crear mapa de actualizaciones
        val updates = mutableMapOf<String, Any>()
        if (name.isNotEmpty()) updates["Nombre"] = name
        if (phone.isNotEmpty()) updates["Telefono"] = phone

        // Actualizar contraseña si se proporciona una nueva
        if (password.isNotEmpty()) {
            auth.currentUser?.updatePassword(password)?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error al actualizar contraseña: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Actualizar datos básicos en Firebase Database
        database.child("usuarios").child(userId).updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Datos actualizados correctamente", Toast.LENGTH_SHORT).show()
                if (imageUri != null) uploadProfileImage(userId)
            } else {
                Toast.makeText(this, "Error al actualizar los datos: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Método para elegir una imagen de galería o cámara
    private fun openImagePicker() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, REQUEST_IMAGE_GALLERY)
    }

    // Manejar el resultado de la selección de imagen
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    imageUri = data?.data
                    profileImageView.setImageURI(imageUri) // Mostrar la imagen seleccionada
                }
            }
        }
    }

    // Subir la foto de perfil a Firebase Storage
    private fun uploadProfileImage(userId: String) {
        val profileImageRef = storage.reference.child("profile_images/$userId.jpg")
        imageUri?.let { uri ->
            profileImageRef.putFile(uri).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error al actualizar la foto de perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

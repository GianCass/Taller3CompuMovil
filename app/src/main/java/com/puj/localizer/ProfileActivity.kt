package com.puj.localizer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.puj.localizer.databinding.ActivityPerfilBinding
import java.io.ByteArrayOutputStream

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
    private var imageBitmap: Bitmap? = null

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
            val option = arrayOf("Tomar una nueva foto de perfil", "Esocger una nueva foto de perfil")
            val menu = AlertDialog.Builder(this)
            menu.setItems(option) { dialog, which ->
                when(which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.CAMERA),
                                REQUEST_IMAGE_CAMERA
                            )
                        } else {
                            takePhoto()
                        }
                    }

                    1 -> {
                        openImagePicker()
                    }
                }
            }
            menu.show()
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
            profileImageView.setImageResource(R.drawable.default_avatar)
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
                if (imageUri != null) uploadProfileImageGallery(userId)
                else if (imageBitmap != null) uploadProfileImagePhoto(userId)
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

    //Tomar una foto
    private fun takePhoto(){
        var cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, REQUEST_IMAGE_CAMERA)
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
                REQUEST_IMAGE_CAMERA -> {
                    imageBitmap = data?.extras?.get("data") as Bitmap
                    profileImageView.setImageBitmap(imageBitmap) // Mostrar la imagen tomada
                }
            }
        }
    }

    // Subir la foto de perfil a Firebase Storage (Galeria)
    private fun uploadProfileImageGallery(userId: String) {
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

    // Subir la foto de perfil a Firebase Storage (Camara)
    private fun uploadProfileImagePhoto(userId: String){
        val baos = ByteArrayOutputStream()
        imageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()
        val profileImageRef = storage.reference.child("profile_images/$userId.jpg")

        val uploadTask = profileImageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            profileImageRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d("Firebase", "Foto subida, URL: $uri")
            }
        }.addOnFailureListener { exception ->
            Log.e("Firebase", "Error en la subida", exception)
        }
    }

    override fun onResume() {
        super.onResume()
        if(auth.currentUser == null){
            logout()
        }
    }

    //Cerrar sesion
    protected fun logout(){
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}

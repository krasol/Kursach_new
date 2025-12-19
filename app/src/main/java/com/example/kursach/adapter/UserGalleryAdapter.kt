package com.example.kursach.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.databinding.ItemUserGalleryPhotoBinding
import java.io.File

class UserGalleryAdapter(
    private var photos: List<String>,
    private val isOwner: Boolean,
    private val onPhotoClick: (position: Int, photoPath: String) -> Unit,
    private val onRemovePhoto: ((photoPath: String) -> Unit)?
) : RecyclerView.Adapter<UserGalleryAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(val binding: ItemUserGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemUserGalleryPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        val context = holder.itemView.context
        val photoFile = File(context.filesDir, photo)

        if (photoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            holder.binding.photoImageView.setImageBitmap(bitmap)
            holder.binding.photoImageView.visibility = View.VISIBLE
            holder.binding.photoPlaceholder.visibility = View.GONE
        } else {
            holder.binding.photoImageView.setImageDrawable(null)
            holder.binding.photoImageView.visibility = View.GONE
            holder.binding.photoPlaceholder.visibility = View.VISIBLE
        }

        holder.binding.root.setOnClickListener {
            onPhotoClick(position, photo)
        }

        if (isOwner && onRemovePhoto != null) {
            holder.binding.removeButton.visibility = View.VISIBLE
            holder.binding.removeButton.setOnClickListener {
                onRemovePhoto.invoke(photo)
            }
        } else {
            holder.binding.removeButton.visibility = View.GONE
            holder.binding.removeButton.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = photos.size

    fun updatePhotos(newPhotos: List<String>) {
        photos = newPhotos
        notifyDataSetChanged()
    }
}










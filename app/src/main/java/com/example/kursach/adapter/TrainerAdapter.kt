package com.example.kursach.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kursach.R
import com.example.kursach.database.JsonDatabase
import com.example.kursach.model.Trainer
import java.io.File

class TrainerAdapter(
    var trainers: List<Trainer>,
    private val onItemClick: (Trainer) -> Unit
) : RecyclerView.Adapter<TrainerAdapter.TrainerViewHolder>() {

    class TrainerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.trainerName)
        val categoryTextView: TextView = itemView.findViewById(R.id.trainerCategory)
        val priceTextView: TextView = itemView.findViewById(R.id.trainerPrice)
        val ratingTextView: TextView = itemView.findViewById(R.id.trainerRating)
        val trainerImage: View = itemView.findViewById(R.id.trainerImage)
        val trainerAvatarImageView: ImageView = itemView.findViewById(R.id.trainerAvatarImageView)
        val trainerAvatarPlaceholder: TextView = itemView.findViewById(R.id.trainerAvatarPlaceholder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrainerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trainer, parent, false)
        return TrainerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrainerViewHolder, position: Int) {
        val trainer = trainers[position]
        holder.nameTextView.text = trainer.name
        // Показываем название хобби, если оно есть, иначе категорию
        holder.categoryTextView.text = if (trainer.hobbyName.isNotEmpty()) {
            trainer.hobbyName
        } else {
            trainer.category
        }
        holder.priceTextView.text = "${trainer.price} ₽/час"
        
        // Calculate rating from reviews
        val reviews = com.example.kursach.database.JsonDatabase.getReviewsForTrainer(trainer.id)
        val rating = if (reviews.isNotEmpty()) {
            reviews.map { it.rating }.average().toFloat()
        } else {
            0f
        }
        holder.ratingTextView.text = "⭐ ${String.format("%.1f", rating)}"
        
        // Загружаем аватарку тренера только из Trainer (не из User)
        // Если у анкеты нет аватарки, показываем только placeholder
        if (trainer.avatar.isNotEmpty()) {
            val avatarFile = File(holder.itemView.context.filesDir, trainer.avatar)
            if (avatarFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                holder.trainerAvatarImageView.setImageBitmap(bitmap)
                holder.trainerAvatarImageView.visibility = View.VISIBLE
                holder.trainerAvatarPlaceholder.visibility = View.GONE
            } else {
                holder.trainerAvatarImageView.visibility = View.GONE
                holder.trainerAvatarPlaceholder.visibility = View.VISIBLE
            }
        } else {
            holder.trainerAvatarImageView.visibility = View.GONE
            holder.trainerAvatarPlaceholder.visibility = View.VISIBLE
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(trainer)
        }
        
        // Click on trainer name to view profile
        holder.nameTextView.setOnClickListener {
            onItemClick(trainer)
        }
    }

    override fun getItemCount() = trainers.size
    
    fun updateTrainers(newTrainers: List<Trainer>) {
        trainers = newTrainers
        notifyDataSetChanged()
    }
}



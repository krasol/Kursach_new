package com.example.kursach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.kursach.databinding.ActivityPhotoViewerBinding
import com.example.kursach.databinding.ItemFullscreenPhotoBinding
import com.google.android.material.tabs.TabLayout
import java.io.File

class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private lateinit var photos: List<String>
    private var startPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photos = intent.getStringArrayListExtra("photos") ?: emptyList()
        startPosition = intent.getIntExtra("position", 0)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "${startPosition + 1} / ${photos.size}"

        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = FullscreenPhotoAdapter(photos)
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startPosition, false)

        // Setup TabLayout indicators
        if (photos.size > 1) {
            binding.tabLayout.visibility = View.VISIBLE
            for (i in photos.indices) {
                binding.tabLayout.addTab(binding.tabLayout.newTab())
            }
            binding.tabLayout.selectTab(binding.tabLayout.getTabAt(startPosition))

            binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
                    supportActionBar?.title = "${position + 1} / ${photos.size}"
                }
            })

            binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.position?.let { binding.viewPager.setCurrentItem(it, true) }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        } else {
            binding.tabLayout.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class FullscreenPhotoAdapter(
        private val photos: List<String>
    ) : RecyclerView.Adapter<FullscreenPhotoAdapter.PhotoViewHolder>() {

        class PhotoViewHolder(val binding: ItemFullscreenPhotoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val binding = ItemFullscreenPhotoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return PhotoViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val photoPath = photos[position]
            val photoFile = File(holder.itemView.context.filesDir, photoPath)

            if (photoFile.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                holder.binding.fullscreenImageView.setImageBitmap(bitmap)
            }
        }

        override fun getItemCount() = photos.size
    }
}













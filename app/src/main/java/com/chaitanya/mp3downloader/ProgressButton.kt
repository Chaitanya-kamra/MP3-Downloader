package com.chaitanya.mp3downloader

import android.content.Context
import android.graphics.Color
import android.text.Layout
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView


class ProgressButton(context: Context,view: View) {
    val cardView :CardView = view.findViewById(R.id.cardView)
    val layout : View? = view.findViewById(R.id.constraintLayout)
    val progressBar :ProgressBar = view.findViewById(R.id.progressBar)
    val btnText : TextView = view.findViewById(R.id.textView)

    fun buttonActivated() {
        progressBar.visibility = View.VISIBLE
        btnText.text = "Grabbing Info…"
        layout!!.setBackgroundColor(Color.parseColor("#9D7CC8"))
    }
    fun buttonDownload() {
        progressBar.visibility = View.GONE
        btnText.text = "Download"
        layout!!.setBackgroundColor(Color.parseColor("#892EFF"))

    }



}
package com.xb.nestedrecycler.adapter

import androidx.recyclerview.widget.RecyclerView
import com.xb.nestedrecycler.ChildRecyclerView

abstract class BaseMultiTypeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    abstract fun getCurrentChildRecyclerView():ChildRecyclerView?
}
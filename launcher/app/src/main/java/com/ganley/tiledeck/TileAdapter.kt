package com.ganley.tiledeck

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TileAdapter(
    private val art: ArtLoader,
    private val onOpen: (Tile) -> Unit,
    private val onOptions: (Tile) -> Unit,
) : ListAdapter<Tile, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) =
        if (getItem(position) is Tile.Header) TYPE_HEADER else TYPE_TILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_header, parent, false) as TextView)
        } else {
            TileVH(inflater.inflate(R.layout.item_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tile = getItem(position)
        when (holder) {
            is HeaderVH -> holder.view.text = (tile as Tile.Header).title
            is TileVH -> holder.bind(tile)
        }
    }

    class HeaderVH(val view: TextView) : RecyclerView.ViewHolder(view)

    inner class TileVH(root: View) : RecyclerView.ViewHolder(root) {
        private val tint: View = root.findViewById(R.id.tint)
        private val glyph: TextView = root.findViewById(R.id.glyph)
        private val icon: ImageView = root.findViewById(R.id.icon)
        private val badge: TextView = root.findViewById(R.id.badge)
        private val label: TextView = root.findViewById(R.id.label)
        private val density = root.resources.displayMetrics.density
        private val ink = root.context.getColor(R.color.ink)
        private val accent = root.context.getColor(R.color.accent)

        init {
            root.clipToOutline = true
            root.setOnFocusChangeListener { v, focused ->
                val s = if (focused) 1.1f else 1f
                v.animate().scaleX(s).scaleY(s).translationZ(if (focused) 12 * density else 0f)
                    .setDuration(140).start()
            }
        }

        fun bind(tile: Tile) {
            itemView.setOnClickListener { onOpen(tile) }
            itemView.setOnLongClickListener { onOptions(tile); true }
            icon.setImageDrawable(null)
            icon.tag = tile.listId
            when (tile) {
                is Tile.App -> bindApp(tile)
                is Tile.Game -> bindGame(tile)
                is Tile.Action -> bindAction(tile)
                is Tile.Header -> Unit
            }
        }

        private fun bindApp(app: Tile.App) {
            plainStyle()
            smallIcon()
            label.text = app.label
            itemView.contentDescription = app.label
            art.appIcon(app) { d -> if (icon.tag == app.listId) icon.setImageDrawable(d) }
        }

        private fun bindGame(game: Tile.Game) {
            tint.visibility = View.VISIBLE
            tint.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(lighten(game.system.color), game.system.color),
            )
            glyph.visibility = View.VISIBLE
            glyph.setTextColor(Color.WHITE)
            glyph.text = initials(game.title)
            badge.visibility = View.VISIBLE
            badge.text = game.system.short
            label.setTextColor(Color.WHITE)
            label.setBackgroundResource(R.drawable.label_scrim)
            label.text = game.title
            itemView.contentDescription = "${game.title}, ${game.system.name}"
            fullBleedIcon()
            art.gameArt(game) { bmp ->
                if (icon.tag == game.listId && bmp != null) {
                    icon.setImageBitmap(bmp)
                    glyph.visibility = View.GONE
                }
            }
        }

        private fun bindAction(action: Tile.Action) {
            plainStyle()
            smallIcon()
            glyph.visibility = View.VISIBLE
            glyph.setTextColor(accent)
            glyph.text = action.glyph
            label.text = action.label
            itemView.contentDescription = action.label
        }

        private fun plainStyle() {
            tint.visibility = View.GONE
            glyph.visibility = View.GONE
            badge.visibility = View.GONE
            label.setTextColor(ink)
            label.background = null
        }

        private fun smallIcon() {
            val size = (72 * density).toInt()
            icon.layoutParams = (icon.layoutParams as FrameLayout.LayoutParams).apply {
                width = size; height = size
                bottomMargin = (18 * density).toInt()
            }
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        private fun fullBleedIcon() {
            icon.layoutParams = (icon.layoutParams as FrameLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                bottomMargin = 0
            }
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TILE = 1

        private val DIFF = object : DiffUtil.ItemCallback<Tile>() {
            override fun areItemsTheSame(a: Tile, b: Tile) = a.listId == b.listId
            override fun areContentsTheSame(a: Tile, b: Tile) = a == b
        }

        fun initials(title: String): String {
            val words = title.split(' ', '-', ':').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
            return when {
                words.isEmpty() -> "?"
                words.size == 1 -> words[0].take(2)
                else -> "${words[0][0]}${words[1][0]}"
            }.uppercase()
        }

        private fun lighten(color: Int): Int {
            val f = 0.35f
            fun ch(c: Int) = (c + (255 - c) * f).toInt()
            return Color.rgb(ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
        }
    }
}

package org.linphone.activities.main.contact.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlin.collections.ArrayList
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R

class SyncAccountAdapter : BaseAdapter() {
    private var accounts: ArrayList<Triple<String, String, Drawable?>> = arrayListOf()

    init {
        accounts.addAll(coreContext.contactsManager.getAvailableSyncAccounts())
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.contact_sync_account_picker_cell, parent, false)
        val account = getItem(position)

        val icon = view.findViewById<ImageView>(R.id.account_icon)
        icon.setImageDrawable(account.third)
        icon.contentDescription = account.second
        val name = view.findViewById<TextView>(R.id.account_name)
        name.text = account.first

        return view
    }

    override fun getItem(position: Int): Triple<String, String, Drawable?> {
        return accounts[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return accounts.size
    }
}

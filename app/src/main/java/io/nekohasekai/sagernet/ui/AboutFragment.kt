package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.widget.ListListener

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    lateinit var binding: LayoutAboutBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_about)

        binding = LayoutAboutBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.aboutScroll, ListListener)

        binding.aboutVersion.text =
            "${SagerNet.appVersionNameForDisplay} (${BuildConfig.VERSION_CODE})"

        binding.aboutLink.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sstpnk/freedom-cat"))
            )
        }
    }

}

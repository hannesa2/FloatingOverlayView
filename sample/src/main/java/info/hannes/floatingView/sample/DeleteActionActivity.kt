package info.hannes.floatingView.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import info.hannes.floatingView.sample.fragment.DeleteActionFragment

class DeleteActionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_action)
        if (savedInstanceState == null) {
            val ft = supportFragmentManager.beginTransaction()
            ft.add(R.id.container, DeleteActionFragment.newInstance(), FRAGMENT_TAG_DELETE_ACTION)
            ft.commit()
        }
    }

    companion object {
        private const val FRAGMENT_TAG_DELETE_ACTION = "delete_action"
    }
}
package com.doppel.myapplication

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var inAppPurchaseHelper: InAppPurchaseHelper
    private  var isConnectionEstablished: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inAppPurchaseHelper = InAppPurchaseHelper(this)
        setContentView(R.layout.activity_main)
        initInAppPurchaseConnection()
        findViewById<Button>(R.id.buyNow).setOnClickListener {
            retrieveProducts()
        }

        inAppPurchaseHelper.connectionStartListener.observe(this) {
            isConnectionEstablished = it
        }
    }


    private fun requestHistory() {
       inAppPurchaseHelper.setNextItem(RequestItems.REQUEST_HISTORY)
    }

    private fun initInAppPurchaseConnection(){
        inAppPurchaseHelper.setNextItem(RequestItems.REQUEST_CONNECTION)
    }

    private fun retrieveProducts() {
        inAppPurchaseHelper.setNextItem(RequestItems.REQUEST_PRODUCTS)
    }
    override fun onResume() {
        super.onResume()
       requestHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppPurchaseHelper.endConnection()
    }
}
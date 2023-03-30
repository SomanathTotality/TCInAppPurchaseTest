package com.doppel.myapplication

import android.app.Activity
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InAppPurchaseHelper(val context: Activity) {
    companion object {
        const val TAG = "InAppPurchaseHelper"
    }

    val connectionStartListener = MutableLiveData<Boolean>()
    private var requestItem = RequestItems.REQUEST_CONNECTION

    fun setNextItem(requestItems: RequestItems) {
        this.requestItem = requestItems
        if(isBillingClientReady()) {
            handleNext()
        } else {
            startConnection()
        }
    }

    val params = QueryPurchaseHistoryParams.newBuilder()
        .setProductType(ProductType.SUBS)

    var acknowledgePurchaseResponseListener =
        AcknowledgePurchaseResponseListener { billingResult ->
            Log.d(
                TAG,
                billingResult.responseCode.toString()
            )
        }

    private val purchaseHistoryResponseListener = PurchaseHistoryResponseListener {billingResult, purchaseHistoryRecords ->
        Log.d(TAG, "purchaseHistoryResponseListener  billingResult = $billingResult")
        Log.d(TAG, "purchaseHistoryResponseListener  purchaseHistoryRecords = $purchaseHistoryRecords")
        handlePurchaseHistory(purchaseHistoryRecords)
    }

    private val purchasesResponseListener = PurchasesResponseListener {
        billingResult, purchases ->
        handlePurchaseResponse(purchases)
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            // To be implemented in a later section.
            Log.d(TAG, "response code = $billingResult.responseCode  " )
            Log.d(TAG, "purchases list  = ${purchases?.toList().toString()} " )
            Log.d(TAG, "purchases  = $purchases " )
            if (billingResult.responseCode == BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    Log.d(TAG, "purchase = ${purchase.orderId}")
                    GlobalScope.launch {
                        handlePurchase(purchase)
                    }
                }
            } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED) {
                Log.d(TAG, "error   = ${billingResult.debugMessage}  " )
                // Handle an error caused by a user cancelling the purchase flow.

            } else {
                Log.d(TAG, " other error   = ${billingResult.responseCode}  " )
                // Handle any other error codes.
            }
        }

    private var billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun startConnection(){
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode ==  BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    connectionStartListener.postValue(true)
                    handleNext()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                connectionStartListener.postValue(false)
                Log.d(TAG, "onBillingServiceDisconnected" )
            }
        })


    }


    private fun launchBillingFlow(productDetails: MutableList<ProductDetails>) {
        Log.d(TAG, " launchBillingFlow")
        val productDetailsParamsList = mutableListOf<BillingFlowParams.ProductDetailsParams>()
        for(productDetail in productDetails) {
            productDetailsParamsList.add(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                .setProductDetails(productDetail)
                .build())
        }
        Log.d(TAG, "productDetailsParamsList = $productDetailsParamsList")
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        Log.d(TAG, "billingFlowParams = $billingFlowParams")
        // Launch the billing flow
        val billingResult = billingClient.launchBillingFlow(context, billingFlowParams)
        Log.d(TAG, "billingResult = $billingResult")
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        // Verify the purchase.
        // Ensure entitlement was not already granted for this purchaseToken.
        // Grant entitlement to the user.
        Log.d(TAG, "handlePurchase purchase = $purchase")
        if (purchase.purchaseState == PurchaseState.PURCHASED) {
            Log.d(TAG, "handlePurchase purchased  = $purchase")
            // Grant entitlement to the user.
            // Acknowledge the purchase if it hasn't already been acknowledged.
            if (!purchase.isAcknowledged) {
                Log.d(TAG, "handlePurchase purchased  but unacknowledged = $purchase")
                //handle un acknowledge
                acknowledgePurchase(purchase)
            } else {
                Log.d(TAG, "handlePurchase purchased and acknowledged  = $purchase")
            }
        } else if (purchase.purchaseState == PurchaseState.PENDING) {
            // Here you can confirm to the user that they've started the pending
            // purchase, and to complete it, they should follow instructions that
            // are given to them. You can also choose to remind the user in the
            // future to complete the purchase if you detect that it is still
            // pending.
            Log.d(TAG, "handlePurchase purchase pending  = $purchase")

        } else {
            val consumeParams =
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            val consumeResult = withContext(Dispatchers.IO) {
                billingClient.consumeAsync(consumeParams
                ) { p0, p1 ->
                    Log.d(TAG, "handlePurchase BillingResult = $p0")
                    Log.d(TAG, "handlePurchase String = $p1")
                    Log.d(TAG, "handlePurchase responseCode = ${p0.responseCode}")
                    Log.d(TAG, "handlePurchase debugMessage = ${p0.debugMessage}")
                }
            }

            Log.d(TAG, "handlePurchase consumeResult = $consumeResult")
        }
    }


    fun retrieveProducts() {
        Log.d(TAG, "retrieveProducts start")

        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(listOf( Product.newBuilder()
                    .setProductId("test_prod_10")
                    .setProductType(ProductType.INAPP)
                    .build()))
                .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                            skuDetailsList ->
            Log.d(TAG, "skuDetailsList ${skuDetailsList}" )
            Log.d(TAG, "billingResult $billingResult" )
            Log.d(TAG, "billingResult responseCode ${billingResult.responseCode}" )
            Log.d(TAG, "billingResult debugMessage  ${billingResult.debugMessage}" )
            if (skuDetailsList.isNotEmpty()) {
                    launchBillingFlow(skuDetailsList)
            } else {
                Log.d(TAG, "No product matches found")
                // No product matches found
            }
            // Process the result
        }
    }

    fun queryPurchaseHistoryAsync(){
        // uses queryPurchaseHistory Kotlin extension function
        GlobalScope.launch {
            val purchaseHistoryResult = billingClient.queryPurchaseHistory(params.build())
            Log.d(TAG, purchaseHistoryResult.toString())
            Log.d(TAG, "responseCode = ${purchaseHistoryResult.billingResult.responseCode}")
            Log.d(TAG, purchaseHistoryResult.billingResult.debugMessage)
            Log.d(TAG,"purchaseHistoryRecordList =  ${purchaseHistoryResult.purchaseHistoryRecordList}")

            purchaseHistoryResult.purchaseHistoryRecordList?.let {
                for(item in it) {
                    Log.d(TAG,"purchaseHistoryRecordList =  ${purchaseHistoryResult.purchaseHistoryRecordList}")
                }
            }

            //to get most recent purchases
            val queryPurchaseHistoryParams = QueryPurchaseHistoryParams
                .newBuilder().
                setProductType(ProductType.INAPP)
                .build()

              billingClient.queryPurchaseHistoryAsync(queryPurchaseHistoryParams, purchaseHistoryResponseListener)

            // to get All purchases Note: querypurchases returns what's cached in the play store app
            // recommended: Cache purchase details on your servers
            // issue with local cache: https://github.com/android/play-billing-samples/issues/139
            val queryPurchasesParams = QueryPurchasesParams
                .newBuilder()
                .setProductType(ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(queryPurchasesParams, purchasesResponseListener)
        }
    }

    fun handlePurchaseHistory(list: MutableList<PurchaseHistoryRecord>?) {
        Log.d(TAG, "PurchaseHistoryRecordList = $list")
        val products = mutableListOf<String>()
        if(list?.isNotEmpty() == true) {
            for(record in list) {
                products.addAll(record.products)
            }
            retrieveProducts(products)
        } else {
            Log.d(TAG, "handlePurchaseHistory is empty or null $list ")
        }
    }

    private fun retrieveProducts(products: MutableList<String>) {
        Log.d(TAG, "retrieveProducts2 start product = $products")

        val productList = mutableListOf<Product>()
        for(product in products) {
            productList.add(Product.newBuilder()
                .setProductId(product)
                .setProductType(ProductType.INAPP)
                .build())
        }
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult,
                                                                            skuDetailsList ->
            Log.d(TAG, "retrieveProducts2 skuDetailsList ${skuDetailsList}" )
            Log.d(TAG, "retrieveProducts2 billingResult $billingResult" )
            Log.d(TAG, "retrieveProducts2 billingResult responseCode ${billingResult.responseCode}" )
            Log.d(TAG, "retrieveProducts2 billingResult debugMessage  ${billingResult.debugMessage}" )
            if (skuDetailsList.isNotEmpty()) {
                // TODO: Discuss to call billing flow or not
//                launchBillingFlow(skuDetailsList)
            } else {
                Log.d(TAG, "No product matches found")
                // No product matches found
            }
            // Process the result
        }
    }

    private fun filterPendingItems(skuDetailsList: List<ProductDetails>) {
        // TODO: need to check for a way to get purchases withour calling billing flow
    }

    private fun handlePurchaseResponse(purchases: List<Purchase>) {
        Log.d(TAG, "purchases = $purchases")
        val pendingList  = mutableListOf<Purchase>()
        for(purchase in purchases) {
            if(purchase.purchaseState == PurchaseState.PENDING) {
                pendingList.add(purchase)
            }
        }

        Log.d(TAG, "Pending List = $pendingList")
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        Log.d(TAG, "acknowledgePurchase")
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build()
        Log.d(TAG, "acknowledgePurchase acknowledgePurchaseParams = $acknowledgePurchaseParams")
        Log.d(TAG, "acknowledgePurchase acknowledgePurchaseParams purchaseToken= ${acknowledgePurchaseParams.purchaseToken}")
        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams,
            acknowledgePurchaseResponseListener
        )
    }

    fun endConnection() {
       billingClient.endConnection()
        Log.d(TAG, "endConnection" )
    }

    fun isBillingClientReady(): Boolean {
        return billingClient.isReady
    }

    private fun handleNext() {
        when(requestItem) {
            RequestItems.REQUEST_CONNECTION -> {
                if(!billingClient.isReady) startConnection()
            }
            RequestItems.REQUEST_HISTORY -> queryPurchaseHistoryAsync()
            RequestItems.REQUEST_PRODUCTS -> retrieveProducts()
        }
    }
}
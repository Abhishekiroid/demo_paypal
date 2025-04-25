package com.example.demo_paypal

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.example.demo_paypal.databinding.ActivityMainBinding
import com.paypal.android.corepayments.CoreConfig
import com.paypal.android.corepayments.Environment
import com.paypal.android.paypalwebpayments.PayPalPresentAuthChallengeResult
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutClient
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutFundingSource
import com.paypal.android.paypalwebpayments.PayPalWebCheckoutRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val mainBinding:ActivityMainBinding by lazy {
        DataBindingUtil.setContentView(this, R.layout.activity_main)
    }

    private val clientID="AVQjx3XNPj8IBvf6wQ9gxBZeryIdPu8PMUaZglq-_cizJLE76ZZM1F0vW4XmgS8AXKsavO8TWxiZrNDF"
    private val secretID="EBce2nCUmufmuSDxfUs5eBqBhtclDYr1BsombthGaokygIt-x1pb_3G7wJJ0X5zHXLeSQOhtNj5TNCgX"
    private val returnUrl="com.example.demo_paypal://demoapp"
    var accessToken: String = ""
    var authState: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainBinding
        AndroidNetworking.initialize(applicationContext)
        fetchAccessToken()
        mainBinding.btnOrderCheckout.setOnClickListener {
            startOrderCheckout()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("onNewIntent: ","Intent received: ${intent.data}")
        if (intent.data?.getQueryParameter("opType")=="payment"){
            Toast.makeText(this,"Payment Success",Toast.LENGTH_SHORT).show()
        }
        else if (intent.data?.getQueryParameter("opType")=="cancel"){
            Toast.makeText(this,"Payment Cancelled",Toast.LENGTH_SHORT).show()
        }

    }
    private fun startOrderCheckout() {
        val uniqueId = UUID.randomUUID().toString()
        val requestJson = JSONObject().apply {
            put("intent", "CAPTURE")
            put("purchase_units", JSONArray().apply {
                put(JSONObject().apply {
                    put("reference_id", uniqueId)
                    put("amount", JSONObject().apply {
                        put("currency_code", "USD")
                        put("value", "10.00")
                    })
                })
            })
            put("payment_source", JSONObject().apply {
                put("paypal", JSONObject().apply {
                    put("experience_context", JSONObject().apply {
                        put("payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED")
                        put("brand_name", "Iroid Demo")
                        put("locale", "en-US")
                        put("landing_page", "LOGIN")
                        put("shipping_preference", "NO_SHIPPING")
                        put("user_action", "PAY_NOW")
                        put("return_url", "https://example.com/return")
                        put("cancel_url", "https://example.com/cancel")
                    })
                })
            })
        }

        AndroidNetworking.post("https://api-m.sandbox.paypal.com/v2/checkout/orders")
            .addHeaders("Content-Type", "application/json")
            .addHeaders("Authorization", "Bearer $accessToken")
            .addHeaders("PayPal-Request-Id", uniqueId)
            .addJSONObjectBody(requestJson)
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    Log.d("success", "Order created successfully: $response")
                    val responseJson = response
                    handleOrderId(responseJson.getString("id"))
                }

                override fun onError(anError: ANError) {
                    Log.d("failure", "Failed to create order: ${anError.message}")
                    Log.d("failure", "Error detail: ${anError.errorDetail}")
                    Log.d("failure", "Response: ${anError.response}")
                    Log.d("failure", "Error body: ${anError.errorBody}")
                }
            })
    }

    private fun handleOrderId(OrderId: String) {
        val config = CoreConfig(clientID, environment = Environment.SANDBOX)
        val payPalWebCheckoutClient = PayPalWebCheckoutClient(this@MainActivity, config, returnUrl)
        val payPalWebCheckoutRequest = PayPalWebCheckoutRequest(orderId = OrderId
        , fundingSource = PayPalWebCheckoutFundingSource.PAYPAL)
        val result=payPalWebCheckoutClient.start(this@MainActivity, payPalWebCheckoutRequest)
        if (result is PayPalPresentAuthChallengeResult.Success) {
            authState = result.authState // Save this to resume later
        } else if (result is PayPalPresentAuthChallengeResult.Failure) {
            // Handle error launching PayPal checkout
            Log.d("failure", "Failed to fetch access token: ${result.error}")
        }
    }

    private fun fetchAccessToken() {
        AndroidNetworking.post("https://api-m.sandbox.paypal.com/v1/oauth2/token")
            .addHeaders("Content-Type", "application/x-www-form-urlencoded")
            .addHeaders("Authorization", "Basic ${Base64.encodeToString("$clientID:$secretID".toByteArray(), Base64.NO_WRAP)}")
            .addBodyParameter("grant_type", "client_credentials")
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    Log.d("onResponse: ", response.getString("access_token").toString())
                    accessToken = response.getString("access_token")
                    mainBinding.btnOrderCheckout.visibility= View.VISIBLE
                    Toast.makeText(this@MainActivity,"Token Fetched", Toast.LENGTH_SHORT).show()
                }

                override fun onError(anError: ANError) {
                    Log.d("failure", "Failed to fetch access token: ${anError.message}")
                }
            })

    }
}
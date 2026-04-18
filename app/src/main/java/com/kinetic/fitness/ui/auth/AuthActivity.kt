
package com.kinetic.fitness.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kinetic.fitness.data.api.RetrofitClient
import com.kinetic.fitness.data.models.LoginRequest
import com.kinetic.fitness.data.models.RegisterRequest
import com.kinetic.fitness.databinding.ActivityAuthBinding
import com.kinetic.fitness.ui.MainActivity
import com.kinetic.fitness.utils.SessionManager
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val vm: AuthViewModel by viewModels()
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        vm.initApi(this)


        if (SessionManager.getInstance(this).isLoggedIn()) {
            startMain(); return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnSubmit.setOnClickListener { handleSubmit() }
        binding.tvToggleMode.setOnClickListener { toggleMode() }
        

        binding.tilName.visibility = View.GONE
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        binding.tilName.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        binding.btnSubmit.text = if (isLoginMode) "ĐĂNG NHẬP" else "TẠO TÀI KHOẢN"
        binding.tvToggleMode.text = if (isLoginMode)
            "Chưa có tài khoản? Đăng ký ngay"
        else
            "Đã có tài khoản? Đăng nhập"
        binding.tvTitle.text = if (isLoginMode) "Chào mừng trở lại" else "Tạo tài khoản"
    }

    private fun handleSubmit() {
        val email = binding.etEmail.text.toString().trim()
        val pass = binding.etPassword.text.toString()

        if (email.isEmpty() || pass.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin"); return
        }

        if (isLoginMode) {
            vm.login(LoginRequest(email, pass))
        } else {
            val name = binding.etName.text.toString().trim()
            if (name.isEmpty()) { showError("Vui lòng nhập tên"); return }
            vm.register(RegisterRequest(name, email, pass))
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSubmit.isEnabled = !loading
        }
        vm.authSuccess.observe(this) { authData ->
            SessionManager.getInstance(this).saveSession(
                authData.token, authData.userId, authData.name, authData.email
            )
            startMain()
        }
        vm.error.observe(this) { showError(it) }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}

class AuthViewModel : ViewModel() {

    private val _loading = androidx.lifecycle.MutableLiveData(false)
    val loading: androidx.lifecycle.LiveData<Boolean> = _loading

    private val _authSuccess = androidx.lifecycle.MutableLiveData<com.kinetic.fitness.data.models.AuthData>()
    val authSuccess: androidx.lifecycle.LiveData<com.kinetic.fitness.data.models.AuthData> = _authSuccess

    private val _error = androidx.lifecycle.MutableLiveData<String>()
    val error: androidx.lifecycle.LiveData<String> = _error

    private var api: com.kinetic.fitness.data.api.ApiService? = null

    fun initApi(context: android.content.Context) {
        if (api == null) api = RetrofitClient.getInstance(context)
    }

    fun login(req: LoginRequest) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = api!!.login(req)
            if (resp.isSuccessful && resp.body()?.success == true) {
                _authSuccess.value = resp.body()!!.data!!
            } else {
                _error.value = resp.body()?.message ?: "Đăng nhập thất bại"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally { _loading.value = false }
    }

    fun register(req: RegisterRequest) = viewModelScope.launch {
        _loading.value = true
        try {
            val resp = api!!.register(req)
            if (resp.isSuccessful && resp.body()?.success == true) {
                _authSuccess.value = resp.body()!!.data!!
            } else {
                _error.value = resp.body()?.message ?: "Đăng ký thất bại"
            }
        } catch (e: Exception) {
            _error.value = "Lỗi kết nối: ${e.message}"
        } finally { _loading.value = false }
    }
}

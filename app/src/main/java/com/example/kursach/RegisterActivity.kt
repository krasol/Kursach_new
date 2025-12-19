package com.example.kursach

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.kursach.data.UserManager
import com.example.kursach.databinding.ActivityRegisterBinding
import com.example.kursach.model.Gender
import com.example.kursach.model.UserType
import com.example.kursach.utils.DocumentUtils

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setupClickListeners()
        setupAgreementText()

        binding.passwordEditText.doAfterTextChanged {
            binding.passwordInputLayout.error = null
        }
        binding.confirmPasswordEditText.doAfterTextChanged {
            binding.confirmPasswordInputLayout.error = null
        }
        binding.confirmPasswordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                register()
                true
            } else {
                false
            }
        }

        binding.agreementCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.agreementErrorText.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            register()
        }
    }

    private fun register() {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        binding.passwordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null
        binding.agreementErrorText.visibility = View.GONE

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка формата имени (должно быть Имя Фамилия - 2 слова)
        val nameParts = name.split(" ").filter { it.isNotBlank() }
        if (nameParts.size < 2) {
            binding.nameEditText.error = "Введите имя и фамилию через пробел"
            Toast.makeText(this, "Введите имя и фамилию через пробел", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка формата email
        val emailPattern = android.util.Patterns.EMAIL_ADDRESS
        if (!emailPattern.matcher(email).matches()) {
            binding.emailEditText.error = "Введите корректный email"
            Toast.makeText(this, "Введите корректный email адрес", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Проверка на занятый email
        val existingUser = com.example.kursach.database.JsonDatabase.getUserByEmail(email)
        if (existingUser != null) {
            binding.emailEditText.error = "Email уже занят"
            Toast.makeText(this, "Email уже занят", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.agreementCheckBox.isChecked) {
            binding.agreementErrorText.visibility = View.VISIBLE
            return
        }

        val passwordErrors = mutableListOf<String>()
        if (password.length < 6) {
            passwordErrors += "Пароль должен содержать минимум 6 символов"
        }
        if (!password.any { it.isUpperCase() }) {
            passwordErrors += "Добавьте хотя бы одну заглавную букву"
        }
        if (!password.any { it.isLowerCase() }) {
            passwordErrors += "Добавьте хотя бы одну строчную букву"
        }
        if (!password.any { it.isDigit() }) {
            passwordErrors += "Добавьте хотя бы одну цифру"
        }
        if (password.all { it.isDigit() }) {
            passwordErrors += "Пароль не должен состоять только из цифр"
        }

        if (passwordErrors.isNotEmpty()) {
            binding.passwordInputLayout.error = passwordErrors.joinToString("\n")
            return
        }

        if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Пароли не совпадают"
            return
        }

        val gender = when {
            binding.radioMale.isChecked -> Gender.MALE
            binding.radioFemale.isChecked -> Gender.FEMALE
            else -> null
        }

        val userType = if (binding.radioTrainer.isChecked) UserType.TRAINER else UserType.USER
        val user = UserManager.register(name, email, password, userType, gender)

        if (user != null) {
            Toast.makeText(this, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            Toast.makeText(this, "Email уже зарегистрирован", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupAgreementText() {
        val baseText = getString(R.string.registration_agreement_text)
        val spannable = SpannableString(baseText)
        val termsPhrase = "Условиями использования"
        val privacyPhrase = "Пользовательским соглашением"

        val termsStart = baseText.indexOf(termsPhrase)
        if (termsStart != -1) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    DocumentUtils.showDocumentDialog(this@RegisterActivity, R.string.terms_title, R.raw.terms)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = binding.agreementsTextView.currentTextColor
                }
            }, termsStart, termsStart + termsPhrase.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val privacyStart = baseText.indexOf(privacyPhrase)
        if (privacyStart != -1) {
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    DocumentUtils.showDocumentDialog(this@RegisterActivity, R.string.privacy_title, R.raw.privacy)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = binding.agreementsTextView.currentTextColor
                }
            }, privacyStart, privacyStart + privacyPhrase.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.agreementsTextView.text = spannable
        binding.agreementsTextView.movementMethod = LinkMovementMethod.getInstance()
        binding.agreementsTextView.highlightColor = 0
    }
}



/*
 * Copyright 2018 Bakumon. https://github.com/Bakumon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.bakumon.moneykeeper.ui.setting.backup

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import com.afollestad.materialdialogs.MaterialDialog
import com.jakewharton.processphoenix.ProcessPhoenix
import me.bakumon.moneykeeper.ConfigManager
import me.bakumon.moneykeeper.Constant
import me.bakumon.moneykeeper.Injection
import me.bakumon.moneykeeper.R
import me.bakumon.moneykeeper.api.ApiEmptyResponse
import me.bakumon.moneykeeper.api.ApiErrorResponse
import me.bakumon.moneykeeper.api.ApiSuccessResponse
import me.bakumon.moneykeeper.api.Network
import me.bakumon.moneykeeper.base.BaseActivity
import me.bakumon.moneykeeper.base.EmptyResource
import me.bakumon.moneykeeper.base.ErrorResource
import me.bakumon.moneykeeper.base.SuccessResource
import me.bakumon.moneykeeper.databinding.ActivitySettingBinding
import me.bakumon.moneykeeper.ui.home.HomeActivity
import me.bakumon.moneykeeper.ui.setting.SettingAdapter
import me.bakumon.moneykeeper.ui.setting.SettingSectionEntity
import me.bakumon.moneykeeper.utill.AndroidUtil
import me.bakumon.moneykeeper.utill.ToastUtils
import me.drakeet.floo.Floo
import okhttp3.HttpUrl
import okhttp3.ResponseBody
import java.util.*

/**
 * 云备份
 *
 * @author Bakumon https://bakumon.me
 */
class BackupActivity : BaseActivity() {
    private lateinit var mBinding: ActivitySettingBinding
    private lateinit var mViewModel: BackupViewModel
    private lateinit var mAdapter: SettingAdapter

    override val layoutId: Int
        get() = R.layout.activity_setting

    override fun onInit(savedInstanceState: Bundle?) {
        mBinding = getDataBinding()
        val viewModelFactory = Injection.provideViewModelFactory()
        mViewModel = ViewModelProviders.of(this, viewModelFactory).get(BackupViewModel::class.java)

        initView()
    }

    private fun initView() {
        mBinding.titleBar?.ibtClose?.setOnClickListener { finish() }
        mBinding.titleBar?.title = getString(R.string.text_cloud_backup)

        mBinding.rvSetting.layoutManager = LinearLayoutManager(this)
        mAdapter = SettingAdapter(null)

        val list = ArrayList<SettingSectionEntity>()

        list.add(SettingSectionEntity(getString(R.string.text_webdav)))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_webdav_url), ConfigManager.webDavUrl)))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_webdav_account), ConfigManager.webDavAccount)))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_webdav_password), getItemDisplayPsw())))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_go_backup), getString(R.string.text_backup_save, getString(R.string.text_webdav) + BackupViewModel.BACKUP_FILE))))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_restore), getString(R.string.text_restore_content, getString(R.string.text_webdav) + BackupViewModel.BACKUP_FILE))))
        list.add(SettingSectionEntity(SettingSectionEntity.Item(getString(R.string.text_webdav_help), Constant.NUTSTORE_HELP_URL)))

        mAdapter.setNewData(list)
        addListener()
        mBinding.rvSetting.adapter = mAdapter
    }

    private fun getItemDisplayPsw(): String {
        return if (ConfigManager.webDAVPsw.isEmpty()) "" else "******"
    }

    private fun addListener() {
        mAdapter.setOnItemClickListener { _, _, position ->
            when (position) {
                1 -> setUrl(position)
                2 -> setAccount(position)
                3 -> setPsw(position)
                4 -> showBackupDialog()
                5 -> showRestoreDialog()
                6 -> AndroidUtil.openWeb(this, Constant.NUTSTORE_HELP_URL)
                else -> {
                }
            }
        }
    }

    private fun setUrl(position: Int) {
        MaterialDialog.Builder(this)
                .title(R.string.text_webdav_url)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .positiveText(R.string.text_affirm)
                .negativeText(R.string.text_cancel)
                .input("", ConfigManager.webDavUrl,
                        { _, input ->
                            val url = input.toString().trim()
                            when {
                                url.isEmpty() -> {
                                    updateUrlItem(url, position)
                                }
                                HttpUrl.parse(url) == null -> ToastUtils.show(R.string.text_url_illegal)
                                else -> {
                                    updateUrlItem(url, position)
                                    // 更新网络配置
                                    Network.updateDavServiceConfig()
                                    initDir()
                                }
                            }
                        }).show()
    }

    private fun updateUrlItem(url: String, position: Int) {
        ConfigManager.setWevDavUrl(url)
        mAdapter.data[position].t.content = ConfigManager.webDavUrl
        mBinding.rvSetting.itemAnimator.changeDuration = 250
        mAdapter.notifyItemChanged(position)
    }

    private fun setAccount(position: Int) {
        MaterialDialog.Builder(this)
                .title(R.string.text_webdav_account)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .positiveText(R.string.text_affirm)
                .negativeText(R.string.text_cancel)
                .input("", ConfigManager.webDavAccount,
                        { _, input ->
                            ConfigManager.setWevDavAccount(input.toString().trim())
                            mAdapter.data[position].t.content = ConfigManager.webDavAccount
                            mBinding.rvSetting.itemAnimator.changeDuration = 250
                            mAdapter.notifyItemChanged(position)
                            // 更新网络配置
                            Network.updateDavServiceConfig()
                            initDir()
                        }).show()
    }

    private var isSaving = false

    private fun setPsw(position: Int) {
        if (isSaving) {
            return
        }
        MaterialDialog.Builder(this)
                .title(R.string.text_webdav_password)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .positiveText(R.string.text_affirm)
                .negativeText(R.string.text_cancel)
                .input("", ConfigManager.webDAVPsw,
                        { _, input ->
                            savePsw(position, input.toString())
                        }).show()
    }

    private fun savePsw(position: Int, input: String) {
        isSaving = true
        ConfigManager.webDAVPsw = input
        mAdapter.data[position].t.content = getItemDisplayPsw()
        mBinding.rvSetting.itemAnimator.changeDuration = 0
        mAdapter.notifyItemChanged(position)
        // 更新网络配置
        Network.updateDavServiceConfig()
        initDir()
        mViewModel.savePsw(input).observe(this, Observer {
            isSaving = false
            when (it) {
                is ErrorResource<Boolean> -> ToastUtils.show(it.errorMessage)
            }
        })
    }

    private fun initDir() {
        if (ConfigManager.webDavUrl.isEmpty() || ConfigManager.webDavAccount.isEmpty() || ConfigManager.webDAVPsw.isEmpty()) {
            return
        }
        mViewModel.getList().observe(this, Observer {
            when (it) {
                is ApiErrorResponse<ResponseBody> -> {
                    if (it.code == 404) {
                        mViewModel.createDir().observe(this, Observer {
                            when (it) {
                                is ApiErrorResponse<ResponseBody> -> ToastUtils.show(it.errorMessage)
                            }
                        })
                    } else {
                        ToastUtils.show(it.errorMessage)
                    }
                }
            }
        })
    }

    private fun showBackupDialog() {
        if (ConfigManager.webDavUrl.isEmpty() || ConfigManager.webDavAccount.isEmpty() || ConfigManager.webDAVPsw.isEmpty()) {
            ToastUtils.show(R.string.text_config_webdav)
            return
        }
        MaterialDialog.Builder(this)
                .title(R.string.text_go_backup)
                .content(R.string.text_backup_save, getString(R.string.text_webdav) + BackupViewModel.BACKUP_FILE)
                .positiveText(R.string.text_affirm)
                .negativeText(R.string.text_cancel)
                .onPositive({ _, _ -> backup() })
                .show()
    }

    private fun backup() {
        mViewModel.getList().observe(this, Observer {
            when (it) {
                is ApiEmptyResponse<ResponseBody> -> backupUpload()
                is ApiSuccessResponse<ResponseBody> -> backupUpload()
                is ApiErrorResponse<ResponseBody> -> {
                    if (it.code == 404) {
                        mViewModel.createDir().observe(this, Observer {
                            when (it) {
                                is ApiSuccessResponse<ResponseBody> -> backupUpload()
                                is ApiEmptyResponse<ResponseBody> -> backupUpload()
                                is ApiErrorResponse<ResponseBody> -> ToastUtils.show(it.errorMessage)
                            }
                        })
                    } else {
                        ToastUtils.show(it.errorMessage)
                    }
                }
            }
        })
    }

    private fun backupUpload() {
        // 上传文件
        mViewModel.backup().observe(this, Observer {
            when (it) {
                is ApiSuccessResponse<ResponseBody> -> ToastUtils.show(R.string.toast_backup_success)
                is ApiEmptyResponse<ResponseBody> -> ToastUtils.show(R.string.toast_backup_success)
                is ApiErrorResponse<ResponseBody> -> ToastUtils.show(it.errorMessage)
            }
        })
    }

    private fun showRestoreDialog() {
        if (ConfigManager.webDavUrl.isEmpty() || ConfigManager.webDavAccount.isEmpty() || ConfigManager.webDAVPsw.isEmpty()) {
            ToastUtils.show(R.string.text_config_webdav)
            return
        }
        MaterialDialog.Builder(this)
                .title(R.string.text_restore)
                .content(R.string.text_restore_content, getString(R.string.text_webdav) + BackupViewModel.BACKUP_FILE)
                .positiveText(R.string.text_affirm)
                .negativeText(R.string.text_cancel)
                .onPositive({ _, _ -> restore() })
                .show()
    }

    private fun restore() {
        mViewModel.restore().observe(this, Observer {
            when (it) {
                is ApiSuccessResponse<ResponseBody> -> {
                    restoreToDB(it.body)
                }
                is ApiErrorResponse<ResponseBody> -> {
                    if (it.code == 404) {
                        ToastUtils.show(R.string.text_backup_file_not_exist)
                    } else {
                        ToastUtils.show(it.errorMessage)
                    }
                }
            }
        })
    }

    private fun restoreToDB(body: ResponseBody) {
        mViewModel.restoreToDB(body).observe(this, Observer {
            when (it) {
                is SuccessResource<Boolean> -> {
                    if (it.body) {
                        ToastUtils.show(R.string.toast_restore_success)
                        backHome()
                    } else {
                        ToastUtils.show(R.string.toast_restore_fail)
                    }
                }
                is EmptyResource -> {
                    restartApp()
                }
                is ErrorResource<Boolean> -> ToastUtils.show(getString(R.string.toast_restore_fail) + "\n" + it.errorMessage)
            }
        })
    }

    private fun backHome() {
        Floo.stack(this)
                .popCount(2)
                .result("refresh")
                .start()
    }

    private fun restartApp() {
        MaterialDialog.Builder(this)
                .cancelable(false)
                .title("\uD83D\uDC7A" + getString(R.string.text_error))
                .content(R.string.text_restore_fail_rollback)
                .positiveText(R.string.text_affirm)
                .onPositive({ _, _ ->
                    ProcessPhoenix.triggerRebirth(this, Intent(this, HomeActivity::class.java))
                })
                .show()
    }

    override fun onDestroy() {
        if (isSaving) {
            ToastUtils.show(R.string.text_saving_psw)
        } else {
            super.onDestroy()
        }
    }

}

package tw.nekomimi.nekogram.transtale

import android.view.View
import cn.hutool.core.util.ArrayUtil
import cn.hutool.core.util.StrUtil
import org.apache.commons.lang3.LocaleUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.transtale.source.GoogleWebTranslator
import tw.nekomimi.nekogram.transtale.source.LingoTranslator
import tw.nekomimi.nekogram.transtale.source.YandexTranslator
import tw.nekomimi.nekogram.utils.PopupBuilder
import tw.nekomimi.nekogram.utils.UIUtil
import tw.nekomimi.nekogram.utils.receive
import tw.nekomimi.nekogram.utils.receiveLazy
import java.util.*

val String.code2Locale: Locale by receiveLazy<String, Locale> {

    val args = replace('-', '_').split('_')

    if (args.size == 1) Locale(args[0]) else Locale(args[0], args[1])

}

val Locale.locale2code by receiveLazy<Locale, String> {

    if (StrUtil.isBlank(getCountry())) {
        language
    } else {
        "$language-$country"
    }

}

val LocaleController.LocaleInfo.locale by receiveLazy<LocaleController.LocaleInfo, Locale> { pluralLangCode.code2Locale }

val Locale.transDb by receive<Locale, TranslateDb> {

    TranslateDb.repo[this] ?: TranslateDb(locale2code).also {

        TranslateDb.repo[this] = it

    }

}

val String.transDbByCode by receive<String, TranslateDb> { code2Locale.transDb }

interface Translator {

    fun doTranslate(from: String, to: String, query: String): String

    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun translate(query: String) = translate(NekoConfig.translateToLang?.code2Locale ?: LocaleController.getInstance().currentLocale, query)

        @Throws(Exception::class)
        @JvmStatic
        fun translate(to: Locale, query: String): String {

            var toLang = to.language

            if (NekoConfig.translationProvider < 3) {

                if (to.language == "zh" && (to.country.toUpperCase() == "CN" || to.country.toUpperCase() == "TW")) {
                    toLang = to.language + "-" + to.country.toUpperCase()
                } else if (to.language == "pt" && to.country in arrayOf("PT", "BR")) {
                    toLang = to.language + "-" + to.country.toUpperCase()
                }

            }

            val translator = when (NekoConfig.translationProvider) {
                in 1..2 -> GoogleWebTranslator
                3 -> LingoTranslator
                4 -> YandexTranslator
                else -> throw IllegalArgumentException()
            }

            // FileLog.d("[Trans] use provider ${translator.javaClass.simpleName}, toLang: $toLang, query: $query")

            return translator.doTranslate("auto", toLang, query)

        }

        @JvmStatic @JvmOverloads fun showTargetLangSelect(anchor: View, type: Int, full: Boolean = false, callback: (Locale) -> Unit) {

            val builder = PopupBuilder(anchor)

            var locales = (if (full) LocaleUtils.availableLocaleList() else LocaleController.getInstance().languages.map { it.pluralLangCode }.toSet().map { it.code2Locale }).filter { it.country.isBlank() && it.variant.isBlank() }.toTypedArray()

            val currLocale = LocaleController.getInstance().currentLocale

            for (i in locales.indices) {

                val defLang = if (type < 2) currLocale else Locale.ENGLISH

                if (locales[i] == defLang) {

                    locales = ArrayUtil.remove(locales, i)
                    locales = ArrayUtil.insert(locales, 0, defLang)

                    break

                }

            }

            val localeNames = arrayOfNulls<String>(if (full) locales.size else locales.size + 1)

            for (i in locales.indices) {

                localeNames[i] = if (i == 0) {

                    LocaleController.getString("Default", R.string.Default) + " ( " + locales[i].getDisplayName(currLocale) + " )"

                } else {

                    locales[i].getDisplayName(currLocale)

                }

            }

            if (!full) {

                localeNames[localeNames.size - 1] = LocaleController.getString("More", R.string.More)

            }

            val finalLocales = locales

            builder.setItems(localeNames.filterIsInstance<CharSequence>().toTypedArray()) { index: Int, _ ->

                if (index == locales.size) {

                    showTargetLangSelect(anchor, type, true, callback)

                } else {

                    if (type == 1) {

                        NekoConfig.setTranslateToLang(finalLocales[index].locale2code)

                    } else if (type == 2) {

                        NekoConfig.setTranslateInputToLang(finalLocales[index].locale2code)

                    }

                    callback(locales[index])

                }

            }

            builder.show()

        }

        @JvmStatic
        @JvmOverloads
        fun translate(to: Locale = LocaleController.getInstance().currentLocale, query: String, translateCallBack: TranslateCallBack) {

            UIUtil.runOnIoDispatcher(Runnable {

                runCatching {

                    val result = translate(to, query)

                    UIUtil.runOnUIThread(Runnable {

                        translateCallBack.onSuccess(result)

                    })

                }.onFailure {

                    translateCallBack.onFailed(it is UnsupportedOperationException, it.message ?: it.javaClass.simpleName)

                }

            })

        }

        interface TranslateCallBack {

            fun onSuccess(translation: String)
            fun onFailed(unsupported: Boolean, message: String)

        }

    }

}
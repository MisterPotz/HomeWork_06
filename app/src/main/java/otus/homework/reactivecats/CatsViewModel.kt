package otus.homework.reactivecats

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class CatsViewModel(
    catsService: CatsService,
    localCatFactsGenerator: LocalCatFactsGenerator,
    context: Context
) : ViewModel() {

    private val _catsLiveData = MutableLiveData<Result>()
    val catsLiveData: LiveData<Result> = _catsLiveData

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    init {
        compositeDisposable.add(
            Flowable.interval(2, TimeUnit.SECONDS)
                .onBackpressureDrop()
                .subscribeOn(Schedulers.io())
                .flatMapSingle {
                    catsService.getCatFact()
                }
                .onErrorResumeNext(Function { throwable ->
                    localCatFactsGenerator
                        .generateCatFactPeriodically()
                        .map { Facts(listOf(it)) }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { fact ->
                        fact.facts.let {
                            if (it.isEmpty()) {
                                _catsLiveData.value =
                                    Error(context.getString(R.string.default_error_text))
                            } else {
                                _catsLiveData.value = Success(it.first())
                            }
                        }
                    },
                    { throwable ->
                        _catsLiveData.value =
                            Error(
                                throwable.message ?: context.getString(R.string.default_error_text)
                            )
                    }
                ))
    }

    override fun onCleared() {
        compositeDisposable.dispose()
    }
}

class CatsViewModelFactory(
    private val catsRepository: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    private val context: Context
) :
    ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        CatsViewModel(catsRepository, localCatFactsGenerator, context) as T
}

sealed class Result
data class Success(val fact: Fact) : Result()
data class Error(val message: String) : Result()
object ServerError : Result()
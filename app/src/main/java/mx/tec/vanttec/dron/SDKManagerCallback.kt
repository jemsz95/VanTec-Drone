package mx.tec.vanttec.dron

import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.sdk.base.BaseProduct
import dji.sdk.sdkmanager.DJISDKManager
import dji.sdk.sdkmanager.DJISDKManager.SDKManagerCallback
import dji.sdk.sdkmanager.DJISDKManager.getInstance
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.subjects.SingleSubject

class SDKManagerCallback : SDKManagerCallback, ObservableOnSubscribe<BaseProduct> {

    val sdkObservable = SingleSubject.create<DJISDKManager>()
    val productObservable = Observable.create(this).publish()!!

    private var observableEmitter: ObservableEmitter<BaseProduct>? = null

    init {
        productObservable.connect()
    }

    override fun onRegister(error: DJIError?) {
        if(error == DJISDKError.REGISTRATION_SUCCESS)
            sdkObservable.onSuccess(getInstance())
        else
            sdkObservable.onError(Exception("Register failed"))
    }

    override fun onProductChange(oldProduct: BaseProduct?, newProduct: BaseProduct?) {
        if(newProduct != null)
            observableEmitter?.onNext(newProduct)
    }

    override fun subscribe(emitter: ObservableEmitter<BaseProduct>) {
        observableEmitter = emitter
    }
}
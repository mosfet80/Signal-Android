package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError.BadgeRedemptionError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.InAppPaymentOneTimeContextJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Currency
import java.util.Locale
import java.util.concurrent.TimeUnit

object OneTimeInAppPaymentRepository {

  private val TAG = Log.tag(OneTimeInAppPaymentRepository::class.java)

  /**
   * Translates the given Throwable into a DonationError
   *
   * If the throwable is already a DonationError, it's returned as is. Otherwise we will return an adequate payment setup error.
   */
  fun <T : Any> handleCreatePaymentIntentError(throwable: Throwable, badgeRecipient: RecipientId, paymentSourceType: PaymentSourceType): Single<T> {
    return if (throwable is DonationError) {
      Single.error(throwable)
    } else {
      val recipient = Recipient.resolved(badgeRecipient)
      val errorSource = if (recipient.isSelf) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT
      Single.error(DonationError.getPaymentSetupError(errorSource, throwable, paymentSourceType))
    }
  }

  /**
   * Checks whether the recipient for the given ID is allowed to receive a gift. Returns
   * normally if they are and emits an error otherwise.
   */
  fun verifyRecipientIsAllowedToReceiveAGift(badgeRecipient: RecipientId): Completable {
    return Completable.fromAction {
      Log.d(TAG, "Verifying badge recipient $badgeRecipient", true)
      val recipient = Recipient.resolved(badgeRecipient)

      if (recipient.isSelf) {
        Log.d(TAG, "Cannot send a gift to self.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
      }

      if (!recipient.isIndividual || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
        Log.w(TAG, "Invalid badge recipient $badgeRecipient. Verification failed.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Parses the donations configuration and returns any boost information from it. Also maps and filters out currencies
   * based on platform and payment method availability.
   */
  fun getBoosts(): Single<Map<Currency, List<Boost>>> {
    return Single.fromCallable { AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { config ->
        config.getBoostAmounts().mapValues { (_, value) ->
          value.map {
            Boost(it)
          }
        }
      }
  }

  fun getBoostBadge(): Single<Badge> {
    return Single
      .fromCallable {
        AppDependencies.donationsService
          .getDonationsConfiguration(Locale.getDefault())
      }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { it.getBoostBadges().first() }
  }

  fun getMinimumDonationAmounts(): Single<Map<Currency, FiatMoney>> {
    return Single.fromCallable { AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .flatMap { it.flattenResult() }
      .subscribeOn(Schedulers.io())
      .map { it.getMinimumDonationAmounts() }
  }

  fun waitForOneTimeRedemption(
    inAppPayment: InAppPaymentTable.InAppPayment,
    paymentIntentId: String
  ): Completable {
    val isLongRunning = inAppPayment.data.paymentMethodType.toPaymentSourceType() == PaymentSourceType.Stripe.SEPADebit
    val isBoost = inAppPayment.data.recipientId?.let { RecipientId.from(it) } == Recipient.self().id
    val donationErrorSource: DonationErrorSource = if (isBoost) DonationErrorSource.ONE_TIME else DonationErrorSource.GIFT

    val timeoutError: DonationError = if (isLongRunning) {
      BadgeRedemptionError.DonationPending(donationErrorSource, inAppPayment)
    } else {
      BadgeRedemptionError.TimeoutWaitingForTokenError(donationErrorSource)
    }

    return Single.fromCallable {
      Log.d(TAG, "Confirmed payment intent. Submitting badge reimbursement job chain.", true)
      SignalDatabase.inAppPayments.update(
        inAppPayment = inAppPayment.copy(
          data = inAppPayment.data.copy(
            redemption = InAppPaymentData.RedemptionState(
              stage = InAppPaymentData.RedemptionState.Stage.INIT,
              paymentIntentId = paymentIntentId
            )
          )
        )
      )

      InAppPaymentOneTimeContextJob.createJobChain(inAppPayment).enqueue()
      inAppPayment.id
    }.flatMap { inAppPaymentId ->
      Log.d(TAG, "Awaiting completion of redemption chain for up to 10 seconds.", true)
      InAppPaymentsRepository.observeUpdates(inAppPaymentId).filter {
        it.state == InAppPaymentTable.State.END
      }.take(1).firstOrError().timeout(10, TimeUnit.SECONDS, Single.error(timeoutError))
    }.map {
      if (it.data.error != null) {
        Log.d(TAG, "Failure during redemption chain: ${it.data.error}", true)
        throw InAppPaymentError(it.data.error)
      }
      it
    }.ignoreElement()
  }
}

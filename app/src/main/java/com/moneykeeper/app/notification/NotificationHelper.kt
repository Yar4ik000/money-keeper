package com.moneykeeper.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.moneykeeper.app.MainActivity
import com.moneykeeper.app.R
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.core.ui.navigation.DeepLinks
import com.moneykeeper.core.ui.util.formatAsCurrency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun showDepositExpiry(
        depositId: Long,
        accountId: Long,
        accountName: String,
        daysLeft: Int,
        endDate: LocalDate,
        projectedAmount: BigDecimal,
    ) {
        val uri = DeepLinks.accountDetail(accountId).toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("deposit_$depositId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val locale = AppLocale.current()
        val daysText = context.resources.getQuantityString(R.plurals.days_left, daysLeft, daysLeft)
        val shortFmt = DateTimeFormatter.ofPattern("d MMM yyyy").withLocale(locale)
        val longFmt  = DateTimeFormatter.ofPattern("d MMMM yyyy").withLocale(locale)
        val amountStr = projectedAmount.formatAsCurrency()

        val notification = NotificationCompat.Builder(context, NotificationChannels.DEPOSIT_EXPIRY)
            .setSmallIcon(R.drawable.ic_notification_deposit)
            .setContentTitle(context.getString(R.string.notif_deposit_expiry_title, daysText))
            .setContentText("$accountName · ${endDate.format(shortFmt)} · $amountStr")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.notif_deposit_expiry_big,
                        accountName,
                        endDate.format(longFmt),
                        amountStr,
                    )
                )
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context)
            .notify(("deposit_$depositId").hashCode(), notification)
    }
}

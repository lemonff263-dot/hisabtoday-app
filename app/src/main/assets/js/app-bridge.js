// AppBridge Client Utility for হিসাব অ্যাপ — Vet সেবা
(function() {
  'use strict';

  var AppUtils = {
    // 1. Download export files (CSV, JSON, Reports)
    saveExportFile: function(fileName, content, mimeType) {
      mimeType = mimeType || 'text/plain;charset=utf-8';
      if (window.AndroidBridge && typeof window.AndroidBridge.downloadFile === 'function') {
        try {
          window.AndroidBridge.downloadFile(content, fileName, mimeType);
          return true;
        } catch(e) {
          console.warn('AndroidBridge.downloadFile failed:', e);
        }
      }
      try {
        var blob = new Blob([content], { type: mimeType });
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        setTimeout(function() {
          document.body.removeChild(a);
          URL.revokeObjectURL(url);
        }, 600);
        return true;
      } catch(err) {
        console.error('saveExportFile error:', err);
        return false;
      }
    },

    // 2. Share text via Android Share Sheet or Web Share API
    shareText: function(text, title) {
      title = title || 'শেয়ার করুন';
      if (window.AndroidBridge && typeof window.AndroidBridge.shareText === 'function') {
        try {
          window.AndroidBridge.shareText(text, title);
          return true;
        } catch(e) {}
      }
      if (navigator.share) {
        navigator.share({ title: title, text: text }).catch(function() {});
        return true;
      }
      return false;
    },

    // 3. Send SMS reminder for dues
    sendDueSms: function(phone, customerName, dueAmount, invoiceNo, shopName) {
      var cleanPhone = String(phone || '').replace(/[^0-9+]/g, '');
      var dueFormatted = (dueAmount !== undefined && dueAmount !== null) ? dueAmount : '0';
      var store = shopName || 'আপনার ভেট ফার্মেসি';
      var cName = customerName || 'শ্রদ্ধেয় খামারি';
      var invText = invoiceNo ? ' (বিল #' + invoiceNo + ')' : '';
      var body = 'শ্রদ্ধেয় ' + cName + ', ' + store + '-এ আপনার বকেয়া বিল ৳' + dueFormatted + invText + '। অনুগ্রহ করে দ্রুত পরিশোধের অনুরোধ করা হচ্ছে। ধন্যবাদ।';

      if (window.AndroidBridge && typeof window.AndroidBridge.sendSms === 'function') {
        try {
          window.AndroidBridge.sendSms(cleanPhone, body);
          return true;
        } catch(e) {
          console.warn('sendSms error:', e);
        }
      }
      window.location.href = 'sms:' + cleanPhone + '?body=' + encodeURIComponent(body);
      return true;
    },

    // 4. Print clean thermal receipt
    printInvoiceReceipt: function(inv, shop) {
      if (!inv) return;
      var sName = (shop && shop.name) ? shop.name : 'হিসাব অ্যাপ — Vet সেবা';
      var sPhone = (shop && shop.phone) ? shop.phone : '';
      var sAddress = (shop && shop.address) ? shop.address : '';
      var invNo = inv.no || inv.id || 'N/A';
      var dateStr = inv.date || inv.at || new Date().toLocaleString('bn-BD');
      var custName = inv.customerName || inv.customer || 'নগদ ক্রেতা';
      var custPhone = inv.customerPhone || '';
      var items = inv.items || [];

      var itemsHtml = '';
      for (var i = 0; i < items.length; i++) {
        var it = items[i];
        var name = it.name || it.medicineName || 'ওষুধ';
        var qty = it.qty || it.quantity || 1;
        var rate = it.rate || it.mrp || 0;
        var total = it.total || (qty * rate);
        itemsHtml += '<tr>' +
          '<td style="padding:4px 2px;border-bottom:1px dashed #ccc;">' + (i + 1) + '. ' + name + '</td>' +
          '<td style="padding:4px 2px;text-align:center;border-bottom:1px dashed #ccc;">' + qty + '</td>' +
          '<td style="padding:4px 2px;text-align:right;border-bottom:1px dashed #ccc;">' + rate + '</td>' +
          '<td style="padding:4px 2px;text-align:right;border-bottom:1px dashed #ccc;">' + total + '</td>' +
        '</tr>';
      }

      var subTotal = inv.subTotal || inv.total || 0;
      var discount = inv.discount || 0;
      var grandTotal = inv.grandTotal || (subTotal - discount);
      var paid = inv.paid || 0;
      var due = inv.due || (grandTotal - paid);

      var receiptHtml = '<!DOCTYPE html><html><head><meta charset="utf-8"><title>রশিদ #' + invNo + '</title>' +
        '<style>' +
        'body { font-family: "Hind Siliguri", "Inter", sans-serif; margin: 0; padding: 12px; font-size: 13px; color: #111; max-width: 320px; margin: 0 auto; }' +
        '.center { text-align: center; }' +
        '.title { font-size: 18px; font-weight: bold; margin: 0; }' +
        '.sub { font-size: 11px; margin: 2px 0 6px 0; color: #555; }' +
        '.divider { border-top: 1px dashed #444; margin: 6px 0; }' +
        '.meta-row { display: flex; justify-content: space-between; font-size: 11.5px; margin: 2px 0; }' +
        'table { width: 100%; border-collapse: collapse; font-size: 12px; margin: 6px 0; }' +
        'th { border-bottom: 1px solid #000; padding: 4px 2px; font-weight: bold; text-align: left; }' +
        '.sum-row { display: flex; justify-content: space-between; padding: 2px 0; font-size: 12.5px; }' +
        '.grand { font-size: 14px; font-weight: bold; border-top: 1px solid #000; border-bottom: 1px solid #000; padding: 4px 0; margin-top: 4px; }' +
        '.footer { text-align: center; font-size: 11px; margin-top: 12px; color: #666; }' +
        '</style></head><body>' +
        '<div class="center">' +
          '<div class="title">' + sName + '</div>' +
          (sAddress ? '<div class="sub">' + sAddress + '</div>' : '') +
          (sPhone ? '<div class="sub">মোবাইল: ' + sPhone + '</div>' : '') +
          '<div class="sub" style="font-weight:bold;margin-top:4px;">বিক্রয় রশিদ</div>' +
        '</div>' +
        '<div class="divider"></div>' +
        '<div class="meta-row"><span>রশিদ নং: #' + invNo + '</span><span>তারিখ: ' + dateStr + '</span></div>' +
        '<div class="meta-row"><span>ক্রেতা: ' + custName + '</span><span>' + custPhone + '</span></div>' +
        '<div class="divider"></div>' +
        '<table><thead><tr><th>বিবরণ</th><th style="text-align:center;">পরিমাণ</th><th style="text-align:right;">দর</th><th style="text-align:right;">মোট</th></tr></thead><tbody>' +
        itemsHtml +
        '</tbody></table>' +
        '<div class="divider"></div>' +
        '<div class="sum-row"><span>মোট:</span><span>৳' + subTotal + '</span></div>' +
        (discount > 0 ? '<div class="sum-row"><span>ছাড়:</span><span>-৳' + discount + '</span></div>' : '') +
        '<div class="sum-row grand"><span>সর্বমোট:</span><span>৳' + grandTotal + '</span></div>' +
        '<div class="sum-row"><span>পরিশোধ:</span><span>৳' + paid + '</span></div>' +
        (due > 0 ? '<div class="sum-row" style="color:#b91c1c;font-weight:bold;"><span>বকেয়া:</span><span>৳' + due + '</span></div>' : '') +
        '<div class="footer"><div>ধন্যবাদ, আবার আসবেন</div><div style="font-size:9.5px;margin-top:2px;">হিসাব অ্যাপ — Vet সেবা</div></div>' +
        '</body></html>';

      if (window.AndroidBridge && typeof window.AndroidBridge.printDocument === 'function') {
        try {
          window.AndroidBridge.printDocument(receiptHtml, 'রশিদ-' + invNo);
          return;
        } catch(e) {
          console.warn('AndroidBridge.printDocument error:', e);
        }
      }

      var printWindow = window.open('', '_blank');
      if (printWindow) {
        printWindow.document.write(receiptHtml);
        printWindow.document.close();
        printWindow.focus();
        setTimeout(function() {
          printWindow.print();
          printWindow.close();
        }, 350);
      }
    }
  };

  window.AppUtils = AppUtils;
  window.saveExportFile = AppUtils.saveExportFile;
  window.shareText = AppUtils.shareText;
  window.sendDueSms = AppUtils.sendDueSms;
  window.printInvoiceReceipt = AppUtils.printInvoiceReceipt;
})();

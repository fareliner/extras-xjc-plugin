/*
 * Copyright 2019 Niels Bertram
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package test.complextypeadapter;

import org.javamoney.moneta.Money;

import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountXmlAdapter extends XmlAdapter<Amount, MonetaryAmount> {

  @Override
  public MonetaryAmount unmarshal(Amount v) {
    return Money.of(v.getValue(), v.getCurrency());
  }

  @Override
  public Amount marshal(MonetaryAmount v) {

    CurrencyUnit currency = v.getCurrency();
    Amount amout = new Amount();
    amout.setCurrency(currency.getCurrencyCode());
    amout.setValue(v.getNumber().numberValue(BigDecimal.class).setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN));

    return amout;

  }

}

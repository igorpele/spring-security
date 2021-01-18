/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.authorization.method;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInvocation;
import reactor.util.annotation.NonNull;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.method.MethodAuthorizationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;

/**
 * An {@link AuthorizationManager} which can determine if an {@link Authentication} has
 * access to the {@link MethodInvocation} by evaluating an expression from the
 * {@link PreAuthorize} annotation.
 *
 * @author Evgeniy Cheban
 * @since 5.5
 */
public final class PreAuthorizeAuthorizationManager implements AuthorizationManager<MethodAuthorizationContext> {

	private final PreAuthorizeExpressionAttributeRegistry registry = new PreAuthorizeExpressionAttributeRegistry();

	private MethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();

	/**
	 * Sets the {@link MethodSecurityExpressionHandler}.
	 * @param expressionHandler the {@link MethodSecurityExpressionHandler} to use
	 */
	public void setExpressionHandler(MethodSecurityExpressionHandler expressionHandler) {
		Assert.notNull(expressionHandler, "expressionHandler cannot be null");
		this.expressionHandler = expressionHandler;
	}

	/**
	 * Determines if an {@link Authentication} has access to the {@link MethodInvocation}
	 * by evaluating an expression from the {@link PreAuthorize} annotation.
	 * @param authentication the {@link Supplier} of the {@link Authentication} to check
	 * @param methodAuthorizationContext the {@link MethodAuthorizationContext} to check
	 * @return an {@link AuthorizationDecision} or null if the {@link PreAuthorize}
	 * annotation is not present
	 */
	@Override
	public AuthorizationDecision check(Supplier<Authentication> authentication,
			MethodAuthorizationContext methodAuthorizationContext) {
		ExpressionAttribute attribute = this.registry.getAttribute(methodAuthorizationContext);
		if (attribute == ExpressionAttribute.NULL_ATTRIBUTE) {
			return null;
		}
		EvaluationContext ctx = this.expressionHandler.createEvaluationContext(authentication.get(),
				methodAuthorizationContext.getMethodInvocation());
		boolean granted = ExpressionUtils.evaluateAsBoolean(attribute.getExpression(), ctx);
		return new AuthorizationDecision(granted);
	}

	private final class PreAuthorizeExpressionAttributeRegistry
			extends AbstractExpressionAttributeRegistry<ExpressionAttribute> {

		@NonNull
		@Override
		ExpressionAttribute resolveAttribute(Method method, Class<?> targetClass) {
			Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
			PreAuthorize preAuthorize = findPreAuthorizeAnnotation(specificMethod);
			if (preAuthorize == null) {
				return ExpressionAttribute.NULL_ATTRIBUTE;
			}
			Expression preAuthorizeExpression = PreAuthorizeAuthorizationManager.this.expressionHandler
					.getExpressionParser().parseExpression(preAuthorize.value());
			return new ExpressionAttribute(preAuthorizeExpression);
		}

		private PreAuthorize findPreAuthorizeAnnotation(Method method) {
			PreAuthorize preAuthorize = AnnotationUtils.findAnnotation(method, PreAuthorize.class);
			return (preAuthorize != null) ? preAuthorize
					: AnnotationUtils.findAnnotation(method.getDeclaringClass(), PreAuthorize.class);
		}

	}

}